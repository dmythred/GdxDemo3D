package com.mygdx.game.components.blender;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.g3d.environment.SpotLight;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.btCapsuleShape;
import com.badlogic.gdx.physics.bullet.collision.btCollisionShape;
import com.badlogic.gdx.physics.bullet.collision.btSphereShape;
import com.badlogic.gdx.utils.Json;
import com.mygdx.game.components.*;
import com.mygdx.game.systems.PhysicsSystem;

import java.util.ArrayList;

/**
 * Created by user on 8/3/15.
 */
public class BlenderComponentsLoader {

	public static final String tag = "BlenderComponentsLoader";
	public ArrayList<Entity> entities = new ArrayList<Entity>();
	AssetManager assets;

	private void setInstanceTransform(ModelInstance instance, Vector3 location, Vector3 rotation, Vector3 scale) {
		for (Node node : instance.nodes) {
			node.scale.set(Math.abs(scale.x), Math.abs(scale.y), Math.abs(scale.z));
		}
		instance.transform.rotate(Vector3.Y, rotation.y);
		instance.transform.rotate(Vector3.X, rotation.x);
		instance.transform.rotate(Vector3.Z, rotation.z);
		instance.transform.setTranslation(location);
		instance.calculateTransforms();
	}

	private Entity createLightEntity(BlenderLightComponent cmp) {
		Entity entity = null;
		Matrix4 transform = new Matrix4();

		if (cmp.type.equals("PointLamp")) {
			entity = new Entity();

			transform.translate(cmp.position);
			entity.add(new TransformComponent(transform));
			entity.add(new LightComponent(
					new PointLight().set(cmp.lamp_color.r, cmp.lamp_color.g, cmp.lamp_color.b,
							cmp.position, cmp.lamp_energy * cmp.lamp_distance / 2)));


		} else if (cmp.type.equals("SpotLamp")) {
			entity = new Entity();

			Vector3 direction = new Vector3(Vector3.Y).scl(-1);
			transform.rotate(Vector3.X, cmp.rotation.x);
			transform.rotate(Vector3.Z, cmp.rotation.z);
			direction.rot(transform);

			transform.translate(cmp.position);

			float intensity = cmp.lamp_energy;
			float cutoffAngle = cmp.lamp_falloff;
			float exponent = 1;

			entity.add(new TransformComponent(transform));
			entity.add(new LightComponent(
					new SpotLight().set(cmp.lamp_color, cmp.position,
							direction, intensity, cutoffAngle, exponent)));


		} else if (cmp.type.equals("SunLamp")) {
			entity = new Entity();

			Vector3 direction = new Vector3(Vector3.Y).scl(-1);
			transform.rotate(Vector3.X, -cmp.rotation.x);
			transform.rotate(Vector3.Z, -cmp.rotation.z);
			direction.rot(transform);

			transform.translate(cmp.position);
			entity.add(new TransformComponent(transform));
			entity.add(new LightComponent(
					new DirectionalLight().set(cmp.lamp_color.r, cmp.lamp_color.g,
							cmp.lamp_color.b, direction.x, direction.y, direction.z)));
		}

		return entity;
	}

	public BlenderComponentsLoader(String modelsJsonPath, String emptiesJsonPath, String lightsJsonPath) {
		assets = new AssetManager();

		ArrayList<BlenderModelComponent> models = loadModels(modelsJsonPath);
		for (BlenderModelComponent cmp : models) {
			cmp.model_file_name = String.format("models/%s.g3db", cmp.model_file_name);
			Gdx.app.debug(tag, "Loading " + cmp.model_file_name);
			assets.load(cmp.model_file_name, Model.class);
		}

		ArrayList<BlenderEmptyComponent> empties = loadEmpties(emptiesJsonPath);
		ArrayList<BlenderLightComponent> lights = loadLights(lightsJsonPath);

		for (BlenderComponent cmp : empties) {
			blenderToGdxCoordinates(cmp);
		}
		for (BlenderModelComponent cmp : models) {
			blenderToGdxCoordinates(cmp);
			Entity entity = createModelEntity(cmp, empties);
			entities.add(entity);
		}
		for (BlenderLightComponent cmp : lights) {
			blenderToGdxCoordinates(cmp);
			Entity entity = createLightEntity(cmp);
			entities.add(entity);
		}
	}

	private static void blenderToGdxCoordinates(BlenderComponent cmp) {
		blenderToGdxCoordinates(cmp.position, cmp.rotation, cmp.scale);
	}

	private static void blenderToGdxCoordinates(Vector3... vs) {
		for (Vector3 v : vs) {
			blenderToGdxCoordinates(v);
		}
	}

	private static Vector3 blenderToGdxCoordinates(Vector3 v) {
		return v.set(v.x, v.z, -v.y);
	}

	private Entity createModelEntity(BlenderModelComponent cmp, ArrayList<BlenderEmptyComponent> empties) {
		Entity entity = new Entity();

		assets.finishLoadingAsset(cmp.model_file_name);
		Model model = assets.get(cmp.model_file_name, Model.class);
		entity.add(new ModelComponent(model, cmp.name));
		ModelInstance instance = entity.getComponent(ModelComponent.class).modelInstance;
		setInstanceTransform(instance, cmp.position, cmp.rotation, cmp.scale);

		btCollisionShape shape = loadCollisionShape(cmp.name, empties);
		boolean isActiveObject = true;

		if (shape == null) {
			shape = Bullet.obtainStaticNodeShape(model.nodes);
			isActiveObject = false;
		}

		PhysicsMotionState motionState = new PhysicsMotionState();
		motionState.transform = instance.transform;

		entity.add(new TransformComponent(instance.transform));

		float mass = loadMass(cmp, empties);

		if (isActiveObject && mass > 0) {
			Gdx.app.debug(tag, String.format("Created active model entity %s with %.2f mass.", cmp.name, mass));
			entity.add(new PhysicsComponent(
					shape, motionState, mass,
					PhysicsSystem.OBJECT_FLAG,
					PhysicsSystem.ALL_FLAG,
					true, false));

		} else {
			Gdx.app.debug(tag, String.format("Created static object %s.", cmp.name, mass));
			entity.add(new PhysicsComponent(
					shape, motionState, 0,
					PhysicsSystem.GROUND_FLAG,
					PhysicsSystem.ALL_FLAG,
					false, false));
		}
		return entity;
	}

	private float loadMass(BlenderModelComponent model, ArrayList<BlenderEmptyComponent>
			empties) {
		for (BlenderEmptyComponent empty : empties) {
			if (empty.name.equals(model.name)
					&& (empty.custom_properties.containsKey("mass"))) {
				return Float.parseFloat(empty.custom_properties.get("mass"));
			}
		}
		return 0;
	}

	private btCollisionShape loadCollisionShape(String modelName, ArrayList<BlenderEmptyComponent>
			empties) {
		btCollisionShape shape = null;
		for (BlenderEmptyComponent empty : empties) {

			if (empty.name.equals(modelName)
					&& (empty.custom_properties.containsKey("collision_shape"))) {

				String shapeType = empty.custom_properties.get("collision_shape");

				if (shapeType.equals("capsule")) {
					float radius = Math.max(Math.abs(empty.scale.x), Math.abs(empty.scale.z));
					shape = new btCapsuleShape(radius, empty.scale.y);

				} else if (shapeType.equals("sphere")) {
					float radius = Math.max(
							Math.max(Math.abs(empty.scale.x), Math.abs(empty.scale.y)),
							Math.abs(empty.scale.z));
					shape = new btSphereShape(radius);
				}
			}
		}
		return shape;
	}

	private ArrayList<BlenderModelComponent> loadModels(String path) {
		return new Json().fromJson(ArrayList.class, BlenderModelComponent.class, Gdx.files.local(path));
	}

	private ArrayList<BlenderEmptyComponent> loadEmpties(String path) {
		return new Json().fromJson(ArrayList.class, BlenderEmptyComponent.class, Gdx.files.local(path));
	}


	private ArrayList<BlenderLightComponent> loadLights(String path) {
		return new Json().fromJson(ArrayList.class, BlenderLightComponent.class, Gdx.files.local(path));
	}

}