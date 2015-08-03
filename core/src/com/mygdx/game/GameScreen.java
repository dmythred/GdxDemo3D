package com.mygdx.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.mygdx.game.components.ModelComponent;
import com.mygdx.game.components.MoveAimComponent;
import com.mygdx.game.components.PhysicsComponent;
import com.mygdx.game.components.blender.BlenderComponentsLoader;
import com.mygdx.game.systems.*;

/**
 * Created by user on 8/1/15.
 */
public class GameScreen extends AbstractScreen {

	PooledEngine engine;

	public GameScreen(int reqWidth, int reqHeight) {
		super(reqWidth, reqHeight);
		engine = new PooledEngine();
		Bullet.init();

		viewportBackgroundColor = new Color(0.28f, 0.56f, 0.83f, 1);

		camera = new PerspectiveCamera(60, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		camera.position.set(100, 100, 100);
		camera.lookAt(0, 0, 0);
		camera.near = 1e-3f;
		camera.far = 3000f;
		camera.update();

		Gdx.app.debug(tag, "Loading environment system");
		ModelEnvironmentSystem envSys = new ModelEnvironmentSystem();
		engine.addEntityListener(envSys.systemFamily, envSys.lightListener);
		engine.addSystem(envSys);

		Gdx.app.debug(tag, "Loading models system");
		ModelRenderSystem modelSys = new ModelRenderSystem(camera, envSys.environment);
		engine.addSystem(modelSys);

		Gdx.app.debug(tag, "Loading physics system");
		PhysicsSystem phySys = new PhysicsSystem();
		engine.addSystem(phySys);
		engine.addEntityListener(phySys.systemFamily, phySys.listener);

		Gdx.app.debug(tag, "Loading json");
		BlenderComponentsLoader loader = new BlenderComponentsLoader(
				"models/test_model.json",
				"models/test_empty.json",
				"models/test_light.json"
		);

		Gdx.app.debug(tag, "Adding entities");
		for (Entity entity : loader.entities) {
			Gdx.app.debug(tag, "Adding" + entity.toString());
			engine.addEntity(entity);
			Gdx.app.debug(tag, "Finished adding" + entity.toString());
		}

		ImmutableArray<Entity> modelEntities = engine.getEntitiesFor(Family.all(ModelComponent.class).get());
		for (Entity entity : modelEntities) {
			ModelComponent modelCmp = entity.getComponent(ModelComponent.class);
			if (modelCmp.id.endsWith("human")) {
				entity.add(new MoveAimComponent());
				Gdx.app.debug(tag, "Added controller to human");
				break;
			}
		}

		Gdx.app.debug(tag, "Adding input controller");
		FPSMoveAimSystem moveSys = new FPSMoveAimSystem();
		engine.addSystem(moveSys);
		Gdx.input.setInputProcessor(moveSys);

		Gdx.app.debug(tag, "Adding camera system");
		CameraMoveAimSystem camSys = new CameraMoveAimSystem(camera);
		engine.addSystem(camSys);

		Gdx.app.debug(tag, "Adding movement system");
		Family phyFamily = Family.all(MoveAimComponent.class, PhysicsComponent.class).get();
		PhysicsMoveAimSystem phyMoveSys = new PhysicsMoveAimSystem(phyFamily);
		engine.addEntityListener(phyFamily, phyMoveSys.listener);
		engine.addSystem(phyMoveSys);

	}

	@Override
	public void render(float delta) {
		super.render(delta);
		engine.update(Gdx.graphics.getDeltaTime());
//		engine.getSystem(PhysicsSystem.class).debugDrawWorld(camera);
	}
}