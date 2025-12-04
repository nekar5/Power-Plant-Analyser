package com.masters.ppa.ui.project.ar;

import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;

/**
 * Node that positions label at the center of the object, always facing the camera
 * The label is visible through the object because objects are semi-transparent
 */
public class ArObjectLabelNode extends Node {
    
    private float objectCenterY = 0f; // Vertical center of the object
    
    /**
     * Set the vertical center Y position of the object (in local coordinates)
     */
    public void setObjectCenterY(float centerY) {
        this.objectCenterY = centerY;
    }
    
    @Override
    public void onUpdate(FrameTime frameTime) {
        super.onUpdate(frameTime);
        Scene scene = getScene();
        if (scene != null && scene.getCamera() != null) {
            Vector3 cameraPosition = scene.getCamera().getWorldPosition();
            
            // Get object position from root node (parent of this label)
            Node rootNode = getParent();
            if (rootNode == null) {
                return;
            }
            
            Vector3 objectPosition = rootNode.getWorldPosition();
            
            // Calculate direction from object center to camera
            Vector3 direction = Vector3.subtract(cameraPosition, objectPosition);
            float distance = direction.length();
            
            if (distance > 0.01f) { // Avoid division by zero
                direction = direction.normalized();
                
                // Position label at the center of the object (0, centerY, 0 in local coordinates)
                setLocalPosition(new Vector3(0f, objectCenterY, 0f));
                
                // Face camera
                setWorldRotation(Quaternion.lookRotation(direction, Vector3.up()));
            }
        }
    }
}


