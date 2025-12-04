package com.masters.ppa.ui.project.ar;

import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;

public class ArRulerLabelNode extends Node {
    @Override
    public void onUpdate(FrameTime frameTime) {
        super.onUpdate(frameTime);
        Scene scene = getScene();
        if (scene != null && scene.getCamera() != null) {
            Vector3 cameraPosition = scene.getCamera().getWorldPosition();
            Vector3 nodePosition = ArRulerLabelNode.this.getWorldPosition();
            Vector3 direction = Vector3.subtract(cameraPosition, nodePosition);
            ArRulerLabelNode.this.setWorldRotation(Quaternion.lookRotation(direction, Vector3.up()));
        }
    }
}


