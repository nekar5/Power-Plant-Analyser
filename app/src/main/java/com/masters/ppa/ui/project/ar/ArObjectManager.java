package com.masters.ppa.ui.project.ar;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.Build;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.TextView;

import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.masters.ppa.R;
import com.masters.ppa.data.model.BatteryItem;
import com.masters.ppa.data.model.BmsItem;
import com.masters.ppa.data.model.ConfigBms;
import com.masters.ppa.data.model.ConfigInverter;
import com.masters.ppa.data.model.ConfigTower;
import com.masters.ppa.data.model.InverterItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Manages AR objects (inverters, battery towers, BMS) placement and manipulation
 */
public class ArObjectManager {
    
    private static final String TAG = "ArObjectManager";
    
    private Context context;
    private ArSceneView arSceneView;
    private Session arSession;
    private Vibrator vibrator;
    
    // Object types
    public enum ObjectType {
        INVERTER, TOWER, STANDALONE_BMS
    }
    
    // Base class for AR objects
    public static class ArObject {
        ObjectType type;
        AnchorNode anchorNode;
        Node rootNode;
        List<VisualNodeInfo> visualNodes = new ArrayList<>();
        List<Node> labelNodes = new ArrayList<>();
        boolean isPlaced = false;
        boolean isPreview = false;
        
        // For manipulation
        float baseY = 0f;
        Vector3 lastTouchPosition;
        float lastRotation = 0f;
        
        // Data
        ConfigInverter configInverter;
        InverterItem inverterItem;
        ConfigTower configTower;
        BatteryItem batteryItem;
        ConfigBms configBms;
        BmsItem bmsItem;
    }
    
    // Helper class to store node info
    private static class VisualNodeInfo {
        Node node;
        Vector3 size;
        Vector3 position;
        
        VisualNodeInfo(Node node, Vector3 size, Vector3 position) {
            this.node = node;
            this.size = size;
            this.position = position;
        }
    }
    
    private List<ArObject> placedObjects = new ArrayList<>();
    private ArObject currentPreviewObject = null;
    private ArObject selectedObject = null;
    
    // Track placed objects count by type for limiting
    private java.util.Map<ObjectType, Integer> placedCounts = new java.util.HashMap<>();
    private java.util.Map<Integer, Integer> placedInverterCounts = new java.util.HashMap<>(); // configInverter id -> count
    private java.util.Map<Integer, Integer> placedTowerCounts = new java.util.HashMap<>(); // configTower id -> count
    private java.util.Map<Integer, Integer> placedBmsCounts = new java.util.HashMap<>(); // configBms id -> count
    
    // Last placed object for undo
    private ArObject lastPlacedObject = null;
    private ObjectType lastPlacedType = null;
    private ConfigInverter lastPlacedConfigInverter = null;
    private ConfigTower lastPlacedConfigTower = null;
    private ConfigBms lastPlacedConfigBms = null;
    private InverterItem lastPlacedInverterItem = null;
    private BatteryItem lastPlacedBatteryItem = null;
    private BmsItem lastPlacedBmsItem = null;
    private BmsItem lastPlacedTowerBmsItem = null;
    
    // Button visibility callback
    public interface ButtonVisibilityCallback {
        void onControlButtonVisibilityChanged(boolean visible);
        void onUndoButtonVisibilityChanged(boolean visible);
        void onResetButtonVisibilityChanged(boolean visible);
    }
    
    private ButtonVisibilityCallback buttonVisibilityCallback;
    
    // Touch handling for manipulation
    private boolean isManipulating = false;
    private float lastTouchX = 0f;
    private float lastTouchY = 0f;
    private float initialDistance = 0f;
    private float initialRotation = 0f;
    private float lastRotation = 0f;
    
    public ArObjectManager(Context context, ArSceneView arSceneView, Session arSession) {
        this.context = context;
        this.arSceneView = arSceneView;
        this.arSession = arSession;
        
        if (context != null) {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
        
        // Touch handling is done through ArSceneView's built-in gesture system
        // We'll handle manipulation through tap and drag gestures
    }
    
    /**
     * Start placing an inverter
     */
    public void startPlacingInverter(ConfigInverter configInverter, InverterItem inverterItem) {
        // If already placed, remove it first for re-placement
        int currentCount = placedInverterCounts.getOrDefault(configInverter.getId(), 0);
        if (currentCount >= 1) {
            removeObjectByConfig(configInverter.getId(), ObjectType.INVERTER);
        }
        
        removePreview();
        currentPreviewObject = createInverterPreview(configInverter, inverterItem);
        updateButtonVisibility();
    }
    
    /**
     * Start placing a battery tower
     */
    public void startPlacingTower(ConfigTower configTower, BatteryItem batteryItem, 
                                  ConfigBms attachedBms, BmsItem bmsItem) {
        // If already placed, remove it first for re-placement
        int currentCount = placedTowerCounts.getOrDefault(configTower.getId(), 0);
        if (currentCount >= 1) {
            removeObjectByConfig(configTower.getId(), ObjectType.TOWER);
        }
        
        removePreview();
        currentPreviewObject = createTowerPreview(configTower, batteryItem, attachedBms, bmsItem);
        // Store tower BMS item for undo
        lastPlacedTowerBmsItem = bmsItem;
        updateButtonVisibility();
    }
    
    /**
     * Start placing a standalone BMS
     */
    public void startPlacingStandaloneBms(ConfigBms configBms, BmsItem bmsItem) {
        // If already placed, remove it first for re-placement
        int currentCount = placedBmsCounts.getOrDefault(configBms.getId(), 0);
        if (currentCount >= 1) {
            removeObjectByConfig(configBms.getId(), ObjectType.STANDALONE_BMS);
        }
        
        removePreview();
        currentPreviewObject = createStandaloneBmsPreview(configBms, bmsItem);
        updateButtonVisibility();
    }
    
    /**
     * Create inverter preview object
     */
    private ArObject createInverterPreview(ConfigInverter configInverter, InverterItem inverterItem) {
        ArObject object = new ArObject();
        object.type = ObjectType.INVERTER;
        object.configInverter = configInverter;
        object.inverterItem = inverterItem;
        object.isPreview = true;
        
        // Convert dimensions from mm to meters
        float width = (float) (inverterItem.getWidth() / 1000.0);
        float height = (float) (inverterItem.getHeight() / 1000.0);
        float depth = (float) (inverterItem.getDepth() / 1000.0);
        
        // Create blue box (opaque for preview)
        Vector3 size = new Vector3(width, height, depth);
        MaterialFactory.makeOpaqueWithColor(context, new Color(0.2f, 0.4f, 1.0f, 1.0f))
                .thenAccept(material -> {
                    ModelRenderable box = ShapeFactory.makeCube(
                        size,
                        Vector3.zero(),
                        material
                    );
                    
                    Node boxNode = new Node();
                    boxNode.setRenderable(box);
                    object.visualNodes.add(new VisualNodeInfo(boxNode, size, Vector3.zero()));
                    
                    // If rootNode already exists (anchor was created), add node immediately
                    if (object.rootNode != null) {
                        boxNode.setParent(object.rootNode);
                    }
                    
                    // Labels are not created during preview
                });
        
        return object;
    }
    
    /**
     * Create battery tower preview object
     */
    private ArObject createTowerPreview(ConfigTower configTower, BatteryItem batteryItem,
                                       ConfigBms attachedBms, BmsItem bmsItem) {
        ArObject object = new ArObject();
        object.type = ObjectType.TOWER;
        object.configTower = configTower;
        object.batteryItem = batteryItem;
        object.configBms = attachedBms;
        object.bmsItem = bmsItem;
        object.isPreview = true;
        
        // Convert dimensions from mm to meters
        float width = (float) (batteryItem.getWidth() / 1000.0);
        float height = (float) (batteryItem.getHeight() / 1000.0);
        float depth = (float) (batteryItem.getDepth() / 1000.0);
        
        int moduleCount = configTower.getBatteriesCount();
        
        // Create green battery modules stacked vertically (opaque for preview)
        MaterialFactory.makeOpaqueWithColor(context, new Color(0.2f, 0.8f, 0.2f, 1.0f))
                .thenAccept(material -> {
                    float totalHeight = height * moduleCount;
                    float moduleY = -totalHeight / 2f + height / 2f;
                    
                    for (int i = 0; i < moduleCount; i++) {
                        Vector3 moduleSize = new Vector3(width, height, depth);
                        ModelRenderable module = ShapeFactory.makeCube(
                            moduleSize,
                            Vector3.zero(),
                            material
                        );
                        
                        Node moduleNode = new Node();
                        moduleNode.setRenderable(module);
                        Vector3 modulePos = new Vector3(0f, moduleY, 0f);
                        moduleNode.setLocalPosition(modulePos);
                        object.visualNodes.add(new VisualNodeInfo(moduleNode, moduleSize, modulePos));
                        
                        // If rootNode already exists (anchor was created), add node immediately
                        if (object.rootNode != null) {
                            moduleNode.setParent(object.rootNode);
                        }
                        
                        moduleY += height;
                    }
                    
                    // Create BMS on top if attached
                    if (attachedBms != null && bmsItem != null) {
                        float bmsWidth = (float) (bmsItem.getWidth() / 1000.0);
                        float bmsHeight = (float) (bmsItem.getHeight() / 1000.0);
                        float bmsDepth = (float) (bmsItem.getDepth() / 1000.0);
                        
                        Vector3 bmsSize = new Vector3(bmsWidth, bmsHeight, bmsDepth);
                        Vector3 bmsPos = new Vector3(0f, totalHeight / 2f + bmsHeight / 2f, 0f);
                        
                        MaterialFactory.makeOpaqueWithColor(context, new Color(1.0f, 0.2f, 0.2f, 1.0f))
                                .thenAccept(bmsMaterial -> {
                                    ModelRenderable bmsBox = ShapeFactory.makeCube(
                                        bmsSize,
                                        Vector3.zero(),
                                        bmsMaterial
                                    );
                                    
                                    Node bmsNode = new Node();
                                    bmsNode.setRenderable(bmsBox);
                                    bmsNode.setLocalPosition(bmsPos);
                                    object.visualNodes.add(new VisualNodeInfo(bmsNode, bmsSize, bmsPos));
                                    
                                    // If rootNode already exists (anchor was created), add node immediately
                                    if (object.rootNode != null) {
                                        bmsNode.setParent(object.rootNode);
                                    }
                                });
                    }
                    
                    // Labels are not created during preview
                });
        
        return object;
    }
    
    /**
     * Create standalone BMS preview object
     */
    private ArObject createStandaloneBmsPreview(ConfigBms configBms, BmsItem bmsItem) {
        ArObject object = new ArObject();
        object.type = ObjectType.STANDALONE_BMS;
        object.configBms = configBms;
        object.bmsItem = bmsItem;
        object.isPreview = true;
        
        // Convert dimensions from mm to meters
        float width = (float) (bmsItem.getWidth() / 1000.0);
        float height = (float) (bmsItem.getHeight() / 1000.0);
        float depth = (float) (bmsItem.getDepth() / 1000.0);
        
        // Create red box (opaque for preview)
        Vector3 size = new Vector3(width, height, depth);
        MaterialFactory.makeOpaqueWithColor(context, new Color(1.0f, 0.2f, 0.2f, 1.0f))
                .thenAccept(material -> {
                    ModelRenderable box = ShapeFactory.makeCube(
                        size,
                        Vector3.zero(),
                        material
                    );
                    
                    Node boxNode = new Node();
                    boxNode.setRenderable(box);
                    object.visualNodes.add(new VisualNodeInfo(boxNode, size, Vector3.zero()));
                    
                    // If rootNode already exists (anchor was created), add node immediately
                    if (object.rootNode != null) {
                        boxNode.setParent(object.rootNode);
                    }
                    
                    // Labels are not created during preview
                });
        
        return object;
    }
    
    /**
     * Create label for inverter
     */
    private void createInverterLabel(ArObject object, InverterItem inverterItem, ConfigInverter configInverter) {
        ViewRenderable.builder()
                .setView(context, R.layout.ar_object_label)
                .build()
                .thenAccept(viewRenderable -> {
                    TextView textView = (TextView) viewRenderable.getView();
                    String text = "Inverter\n" + 
                                 inverterItem.getName() + "\n" + 
                                 String.format(Locale.getDefault(), "%.1f kW", inverterItem.getPowerKw());
                    textView.setText(text);
                    viewRenderable.setShadowCaster(false);
                    
                    // Create label node that positions itself at the center of the object
                    // Only add to scene if object is placed (not preview)
                    if (!object.isPreview) {
                        ArObjectLabelNode labelNode = new ArObjectLabelNode();
                        labelNode.setRenderable(viewRenderable);
                        float height = (float) (inverterItem.getHeight() / 1000.0);
                        // Set object center Y (vertical middle)
                        labelNode.setObjectCenterY(height / 2f);
                        // Position at center, will be adjusted by ArObjectLabelNode
                        labelNode.setLocalPosition(new Vector3(0f, height / 2f, 0f));
                        object.labelNodes.add(labelNode);
                        
                        // If rootNode already exists, add label immediately
                        if (object.rootNode != null) {
                            labelNode.setParent(object.rootNode);
                        }
                    }
                });
    }
    
    /**
     * Create label for battery tower
     */
    private void createTowerLabel(ArObject object, BatteryItem batteryItem) {
        ViewRenderable.builder()
                .setView(context, R.layout.ar_object_label)
                .build()
                .thenAccept(viewRenderable -> {
                    TextView textView = (TextView) viewRenderable.getView();
                    int towerNumber = object.configTower != null ? object.configTower.getTowerNumber() : 0;
                    int batteryCount = object.configTower != null ? object.configTower.getBatteriesCount() : 0;
                    double totalCapacity = batteryItem.getCapacityKWh() * batteryCount;
                    String text = "Tower " + towerNumber + "\n";
                    // Add BMS info if present (before battery type)
                    if (object.configBms != null && object.bmsItem != null) {
                        text += "BMS: " + object.bmsItem.getName() + "\n";
                    }
                    text += batteryItem.getName() + "\n" + 
                            String.format(Locale.getDefault(), "%.2f kWh", totalCapacity) + "\n" +
                            batteryCount + " batteries";
                    textView.setText(text);
                    viewRenderable.setShadowCaster(false);
                    
                    // Create label node that positions itself at the center of the object
                    // Only add to scene if object is placed (not preview)
                    if (!object.isPreview) {
                        ArObjectLabelNode labelNode = new ArObjectLabelNode();
                        labelNode.setRenderable(viewRenderable);
                        // Calculate tower center: modules go from -totalHeight/2 to totalHeight/2
                        // Get dimensions from battery item
                        float height = (float) (batteryItem.getHeight() / 1000.0);
                        int moduleCount = object.configTower != null ? object.configTower.getBatteriesCount() : 0;
                        float totalHeight = height * moduleCount;
                        float towerCenterY = 0f; // Center of battery modules (they're centered around 0)
                        if (object.configBms != null && object.bmsItem != null) {
                            float bmsHeight = (float) (object.bmsItem.getHeight() / 1000.0);
                            // BMS is on top at totalHeight/2 + bmsHeight/2
                            // Overall structure: from -totalHeight/2 to totalHeight/2 + bmsHeight
                            // Center is at: (-totalHeight/2 + totalHeight/2 + bmsHeight) / 2 = bmsHeight/2
                            towerCenterY = bmsHeight / 2f;
                        }
                        // Set object center Y (vertical middle of the tower)
                        labelNode.setObjectCenterY(towerCenterY);
                        // Position at center, will be adjusted by ArObjectLabelNode
                        labelNode.setLocalPosition(new Vector3(0f, towerCenterY, 0f));
                        object.labelNodes.add(labelNode);
                        
                        // If rootNode already exists, add label immediately
                        if (object.rootNode != null) {
                            labelNode.setParent(object.rootNode);
                        }
                    }
                });
    }
    
    /**
     * Create label for BMS
     */
    private void createBmsLabel(ArObject object, BmsItem bmsItem) {
        ViewRenderable.builder()
                .setView(context, R.layout.ar_object_label)
                .build()
                .thenAccept(viewRenderable -> {
                    TextView textView = (TextView) viewRenderable.getView();
                    textView.setText(bmsItem.getName());
                    viewRenderable.setShadowCaster(false);
                    
                    // Create label node that positions itself at the center of the object
                    // Only add to scene if object is placed (not preview)
                    if (!object.isPreview) {
                        ArObjectLabelNode labelNode = new ArObjectLabelNode();
                        labelNode.setRenderable(viewRenderable);
                        float height = (float) (bmsItem.getHeight() / 1000.0);
                        // Set object center Y (vertical middle)
                        labelNode.setObjectCenterY(height / 2f);
                        // Position at center, will be adjusted by ArObjectLabelNode
                        labelNode.setLocalPosition(new Vector3(0f, height / 2f, 0f));
                        object.labelNodes.add(labelNode);
                        
                        // If rootNode already exists, add label immediately
                        if (object.rootNode != null) {
                            labelNode.setParent(object.rootNode);
                        }
                    }
                });
    }
    
    /**
     * Update preview object position based on AR hit test
     */
    public void updatePreview(FrameTime frameTime) {
        if (currentPreviewObject == null || arSession == null) {
            return;
        }
        
        try {
            Frame frame = arSession.update();
            if (frame == null) {
                return;
            }
            
            // Get hit result at center of screen
            float[] hitTestArray = new float[2];
            hitTestArray[0] = arSceneView.getWidth() / 2f;
            hitTestArray[1] = arSceneView.getHeight() / 2f;
            
            List<HitResult> hitResults = frame.hitTest(hitTestArray[0], hitTestArray[1]);
            
            if (!hitResults.isEmpty()) {
                HitResult hitResult = hitResults.get(0);
                Trackable trackable = hitResult.getTrackable();
                
                if (trackable instanceof Plane) {
                    Plane plane = (Plane) trackable;
                    if (plane.getTrackingState() == com.google.ar.core.TrackingState.TRACKING) {
                        Plane.Type planeType = plane.getType();
                        if (planeType == Plane.Type.HORIZONTAL_UPWARD_FACING || 
                            planeType == Plane.Type.VERTICAL) {
                            
                            Pose hitPose = hitResult.getHitPose();
                            
                            // Calculate object height to position it correctly on plane
                            float objectHeight = getObjectHeight(currentPreviewObject);
                            
                            // Adjust Y position so object sits on plane (not half-buried)
                            float[] hitTranslation = new float[3];
                            hitPose.getTranslation(hitTranslation, 0);
                            
                            // Initialize rotation
                            float[] rotation = new float[4];
                            hitPose.getRotationQuaternion(rotation, 0);
                            
                            // Adjust position based on plane type
                            if (planeType == Plane.Type.HORIZONTAL_UPWARD_FACING) {
                                // Horizontal plane - place object on top
                                if (currentPreviewObject.type == ObjectType.TOWER) {
                                    // Tower modules go from -totalHeight/2 to totalHeight/2 in local coordinates
                                    // Bottom of tower (first module bottom) is at -totalHeight/2
                                    // To place bottom on plane, anchor Y should be at hit point Y + totalHeight/2
                                    // But objectHeight includes BMS if present, so we need just battery height
                                    float batteryHeight = (float) (currentPreviewObject.batteryItem.getHeight() / 1000.0);
                                    int batteryCount = currentPreviewObject.configTower != null ? 
                                        currentPreviewObject.configTower.getBatteriesCount() : 0;
                                    float batteryTotalHeight = batteryHeight * batteryCount;
                                    // Anchor at plane level, so bottom of batteries is on plane
                                    hitTranslation[1] = hitTranslation[1] + batteryTotalHeight / 2f;
                                } else {
                                    // For inverters and BMS, object center is at 0
                                    // Object center should be at hit point Y + half object height
                                    hitTranslation[1] = hitTranslation[1] + objectHeight / 2f;
                                }
                            } else {
                                // Vertical plane (wall)
                                if (currentPreviewObject.type == ObjectType.INVERTER) {
                                    // Inverter on wall - horizontal, wide side to wall, aligned with wall orientation
                                    float depth = (float) (currentPreviewObject.inverterItem.getDepth() / 1000.0);
                                    
                                    // Get wall normal from hitPose
                                    // hitPose's Z-axis points outward from the wall (normal)
                                    float[] wallNormal = new float[3];
                                    hitPose.getTransformedAxis(2, 0, wallNormal, 0);
                                    
                                    // Get wall's up direction (Y-axis of hitPose points upward along wall)
                                    float[] wallUp = new float[3];
                                    hitPose.getTransformedAxis(1, 0, wallUp, 0);
                                    
                                    // We want the inverter's back face (Width x Height) to be parallel to the wall
                                    // The back face is perpendicular to the inverter's local Z-axis
                                    // So we need to align the inverter's -Z axis with the wall's normal
                                    // This means the inverter's Z should point opposite to wall normal
                                    
                                    // Create a rotation that:
                                    // 1. Makes inverter's -Z point in the direction of wall normal (so back faces wall)
                                    // 2. Keeps inverter's Y aligned with wall's up direction
                                    // 3. Keeps inverter's X aligned with wall's right direction
                                    
                                    // The wall's coordinate system:
                                    // - Z = normal (outward)
                                    // - Y = up (vertical along wall)
                                    // - X = right (horizontal along wall)
                                    
                                    // We want inverter's coordinate system to match:
                                    // - Inverter's -Z = wall's Z (normal)
                                    // - Inverter's Y = wall's Y (up)
                                    // - Inverter's X = wall's X (right)
                                    
                                    // This means inverter's Z = -wall's Z
                                    // So we need to rotate 180 degrees around Y axis from hitPose orientation
                                    
                                    // Get rotation from hitPose
                                    hitPose.getRotationQuaternion(rotation, 0);
                                    
                                    // Convert to Quaternion for manipulation
                                    Quaternion hitRotation = new Quaternion(rotation[0], rotation[1], rotation[2], rotation[3]);
                                    
                                    // Rotate 180 degrees around Y axis to flip Z direction
                                    // This makes the inverter's back face (negative Z) face the wall
                                    Quaternion yRot180 = Quaternion.axisAngle(new Vector3(0f, 1f, 0f), 0f);// v:180 (off, useless but scary to delete)
                                    Quaternion rotated = Quaternion.multiply(hitRotation, yRot180);
                                    
                                    // Now rotate 90 degrees around X axis to make Width x Height face the wall
                                    // Instead of Width x Depth. This aligns the largest face (back) with the wall
                                    Quaternion xRot90 = Quaternion.axisAngle(new Vector3(1f, 0f, 0f), 90f);
                                    Quaternion finalRotation = Quaternion.multiply(rotated, xRot90);

                                    // Convert back to float array
                                    rotation[0] = finalRotation.x;
                                    rotation[1] = finalRotation.y;
                                    rotation[2] = finalRotation.z;
                                    rotation[3] = finalRotation.w;
                                    
                                    // Move object away from wall by half depth in direction of wall normal
                                    hitTranslation[0] += wallNormal[0] * (depth / 2f);
                                    hitTranslation[1] += wallNormal[1] * (depth / 2f);
                                    hitTranslation[2] += wallNormal[2] + (depth / 2f); // !!! apparently after rotations z = 0
                                } else if (currentPreviewObject.type == ObjectType.TOWER || 
                                          currentPreviewObject.type == ObjectType.STANDALONE_BMS) {
                                    // Towers and BMS on wall - stand on floor near wall
                                    // Find horizontal floor plane at this Y position
                                    List<Plane> allPlanes = new ArrayList<>();
                                    for (Plane p : arSession.getAllTrackables(Plane.class)) {
                                        if (p.getTrackingState() == com.google.ar.core.TrackingState.TRACKING &&
                                            p.getType() == Plane.Type.HORIZONTAL_UPWARD_FACING) {
                                            allPlanes.add(p);
                                        }
                                    }
                                    
                                    // Find closest horizontal plane to hit point Y
                                    Plane floorPlane = null;
                                    float minDistance = Float.MAX_VALUE;
                                    for (Plane hp : allPlanes) {
                                        float[] planeCenter = new float[3];
                                        hp.getCenterPose().getTranslation(planeCenter, 0);
                                        float distance = Math.abs(planeCenter[1] - hitTranslation[1]);
                                        if (distance < minDistance) {
                                            minDistance = distance;
                                            floorPlane = hp;
                                        }
                                    }
                                    
                                    if (floorPlane != null) {
                                        // Get floor plane Y position
                                        float[] floorCenter = new float[3];
                                        floorPlane.getCenterPose().getTranslation(floorCenter, 0);
                                        
                                        // Position object on floor
                                        if (currentPreviewObject.type == ObjectType.TOWER) {
                                            float batteryHeight = (float) (currentPreviewObject.batteryItem.getHeight() / 1000.0);
                                            int batteryCount = currentPreviewObject.configTower != null ? 
                                                currentPreviewObject.configTower.getBatteriesCount() : 0;
                                            float batteryTotalHeight = batteryHeight * batteryCount;
                                            hitTranslation[1] = floorCenter[1] + batteryTotalHeight / 2f;
                                        } else {
                                            // BMS
                                            hitTranslation[1] = floorCenter[1] + objectHeight / 2f;
                                        }
                                        
                                        // Get wall normal to move object away from wall
                                        float[] planeNormal = new float[3];
                                        Pose planeCenterPose = plane.getCenterPose();
                                        planeCenterPose.getTransformedAxis(2, 0, planeNormal, 0);
                                        
                                        // Move object away from wall (use object width or depth, whichever is larger)
                                        float objectDepth;
                                        if (currentPreviewObject.type == ObjectType.TOWER) {
                                            objectDepth = (float) (currentPreviewObject.batteryItem.getDepth() / 1000.0);
                                        } else {
                                            objectDepth = (float) (currentPreviewObject.bmsItem.getDepth() / 1000.0);
                                        }
                                        float offset = objectDepth / 2f + 0.01f; // Add small margin
                                        
                                        hitTranslation[0] += planeNormal[0] * offset;
                                        hitTranslation[2] += planeNormal[2] * offset;
                                        
                                        // Use horizontal plane rotation (identity for vertical objects)
                                        rotation[0] = 0f;
                                        rotation[1] = 0f;
                                        rotation[2] = 0f;
                                        rotation[3] = 1f;
                                    } else {
                                        // No floor plane found, skip this frame
                                        return;
                                    }
                                } else {
                                    return; // Skip this frame
                                }
                            }
                            // For horizontal planes, rotation is already set from hitPose above
                            
                            // Create modified pose
                            Pose adjustedPose = new Pose(hitTranslation, rotation);
                            
                            // Create or update anchor
                            if (currentPreviewObject.anchorNode == null) {
                                Anchor anchor = arSession.createAnchor(adjustedPose);
                                currentPreviewObject.anchorNode = new AnchorNode(anchor);
                                currentPreviewObject.anchorNode.setParent(arSceneView.getScene());
                                
                                // Add all visual nodes
                                currentPreviewObject.rootNode = new Node();
                                currentPreviewObject.rootNode.setParent(currentPreviewObject.anchorNode);
                                
                                for (VisualNodeInfo visualNodeInfo : currentPreviewObject.visualNodes) {
                                    visualNodeInfo.node.setParent(currentPreviewObject.rootNode);
                                }
                                
                                // Labels are not added during preview
                            } else {
                                // Update position
                                Anchor newAnchor = arSession.createAnchor(adjustedPose);
                                currentPreviewObject.anchorNode.setAnchor(newAnchor);
                            }
                            
                            // Store base position for manipulation
                            // For horizontal planes, store Y
                            // For vertical planes, store the position on the plane
                            if (planeType == Plane.Type.HORIZONTAL_UPWARD_FACING) {
                                currentPreviewObject.baseY = hitTranslation[1];
                            } else if (planeType == Plane.Type.VERTICAL) {
                                // For vertical planes, store Y for vertical movement along wall
                                currentPreviewObject.baseY = hitTranslation[1];
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore errors during preview update
        }
    }
    
    /**
     * Place current preview object
     */
    public boolean placeCurrentObject() {
        if (currentPreviewObject == null || currentPreviewObject.anchorNode == null) {
            return false;
        }
        
        // Check if we can place more of this type
        if (!canPlaceMore(currentPreviewObject)) {
            if (errorCallback != null) {
                errorCallback.onPlacementError("Maximum number of this object type already placed");
            }
            return false;
        }
        
        // Check for intersection with existing objects
        if (checkIntersection(currentPreviewObject)) {
            // Show error message through callback
            if (errorCallback != null) {
                errorCallback.onPlacementError("Invalid placement: Objects are overlapping");
            }
            return false;
        }
        
        // Store info for undo
        lastPlacedObject = currentPreviewObject;
        lastPlacedType = currentPreviewObject.type;
        lastPlacedConfigInverter = currentPreviewObject.configInverter;
        lastPlacedConfigTower = currentPreviewObject.configTower;
        lastPlacedConfigBms = currentPreviewObject.configBms;
        lastPlacedInverterItem = currentPreviewObject.inverterItem;
        lastPlacedBatteryItem = currentPreviewObject.batteryItem;
        lastPlacedBmsItem = currentPreviewObject.bmsItem;
        
        // Convert preview to placed object
        currentPreviewObject.isPreview = false;
        currentPreviewObject.isPlaced = true;
        
        // Make objects semi-transparent and show labels
        updateObjectMaterial(currentPreviewObject);
        showLabels(currentPreviewObject);
        
        placedObjects.add(currentPreviewObject);
        
        // Update counts
        updatePlacedCounts(currentPreviewObject, true);
        
        currentPreviewObject = null;
        
        updateButtonVisibility();
        vibrate();
        return true;
    }
    
    /**
     * Check if we can place more objects of this type
     */
    private boolean canPlaceMore(ArObject object) {
        switch (object.type) {
            case INVERTER:
                if (object.configInverter != null) {
                    int currentCount = placedInverterCounts.getOrDefault(object.configInverter.getId(), 0);
                    // Can place at least one of each inverter in config
                    return currentCount < 1; // Each inverter can be placed once
                }
                return false;
            case TOWER:
                if (object.configTower != null) {
                    int currentCount = placedTowerCounts.getOrDefault(object.configTower.getId(), 0);
                    // Can place at least one of each tower in config
                    return currentCount < 1; // Each tower can be placed once
                }
                return false;
            case STANDALONE_BMS:
                if (object.configBms != null) {
                    int currentCount = placedBmsCounts.getOrDefault(object.configBms.getId(), 0);
                    // Can place at least one of each standalone BMS in config
                    return currentCount < 1; // Each standalone BMS can be placed once
                }
                return false;
            default:
                return false;
        }
    }
    
    /**
     * Update placed object counts
     */
    private void updatePlacedCounts(ArObject object, boolean add) {
        int delta = add ? 1 : -1;
        switch (object.type) {
            case INVERTER:
                if (object.configInverter != null) {
                    int count = placedInverterCounts.getOrDefault(object.configInverter.getId(), 0);
                    placedInverterCounts.put(object.configInverter.getId(), count + delta);
                }
                break;
            case TOWER:
                if (object.configTower != null) {
                    int count = placedTowerCounts.getOrDefault(object.configTower.getId(), 0);
                    placedTowerCounts.put(object.configTower.getId(), count + delta);
                }
                break;
            case STANDALONE_BMS:
                if (object.configBms != null) {
                    int count = placedBmsCounts.getOrDefault(object.configBms.getId(), 0);
                    placedBmsCounts.put(object.configBms.getId(), count + delta);
                }
                break;
        }
    }
    
    /**
     * Undo last placement - remove last placed object and allow re-placing
     */
    public void undoLastPlacement() {
        if (placedObjects.isEmpty()) {
            return;
        }
        
        // Remove the last placed object
        ArObject toRemove = placedObjects.get(placedObjects.size() - 1);
        
        // Remove from scene
        if (toRemove.anchorNode != null) {
            arSceneView.getScene().removeChild(toRemove.anchorNode);
        }
        placedObjects.remove(toRemove);
        
        // Update counts
        updatePlacedCounts(toRemove, false);
        
        // Store info for re-placing
        lastPlacedType = toRemove.type;
        lastPlacedConfigInverter = toRemove.configInverter;
        lastPlacedConfigTower = toRemove.configTower;
        lastPlacedConfigBms = toRemove.configBms;
        lastPlacedInverterItem = toRemove.inverterItem;
        lastPlacedBatteryItem = toRemove.batteryItem;
        lastPlacedBmsItem = toRemove.bmsItem;
        
        // Re-create preview for re-placing
        switch (lastPlacedType) {
            case INVERTER:
                if (lastPlacedConfigInverter != null && lastPlacedInverterItem != null) {
                    startPlacingInverter(lastPlacedConfigInverter, lastPlacedInverterItem);
                }
                break;
            case TOWER:
                if (lastPlacedConfigTower != null && lastPlacedBatteryItem != null) {
                    startPlacingTower(lastPlacedConfigTower, lastPlacedBatteryItem,
                                     lastPlacedConfigBms, lastPlacedBmsItem);
                }
                break;
            case STANDALONE_BMS:
                if (lastPlacedConfigBms != null && lastPlacedBmsItem != null) {
                    startPlacingStandaloneBms(lastPlacedConfigBms, lastPlacedBmsItem);
                }
                break;
        }
        
        updateButtonVisibility();
        vibrate();
    }
    
    /**
     * Remove specific object and allow re-placing (called when selecting already placed object from dialog)
     */
    public void removeObjectForReplacement(ConfigInverter configInverter, InverterItem inverterItem) {
        removeObjectByConfig(configInverter.getId(), ObjectType.INVERTER);
        if (configInverter != null && inverterItem != null) {
            startPlacingInverter(configInverter, inverterItem);
        }
    }
    
    public void removeObjectForReplacement(ConfigTower configTower, BatteryItem batteryItem, 
                                          ConfigBms attachedBms, BmsItem bmsItem) {
        removeObjectByConfig(configTower.getId(), ObjectType.TOWER);
        if (configTower != null && batteryItem != null) {
            startPlacingTower(configTower, batteryItem, attachedBms, bmsItem);
        }
    }
    
    public void removeObjectForReplacement(ConfigBms configBms, BmsItem bmsItem) {
        removeObjectByConfig(configBms.getId(), ObjectType.STANDALONE_BMS);
        if (configBms != null && bmsItem != null) {
            startPlacingStandaloneBms(configBms, bmsItem);
        }
    }
    
    /**
     * Remove object by config ID and type
     */
    private void removeObjectByConfig(int configId, ObjectType type) {
        ArObject toRemove = null;
        for (ArObject obj : placedObjects) {
            boolean matches = false;
            switch (type) {
                case INVERTER:
                    matches = obj.configInverter != null && obj.configInverter.getId() == configId;
                    break;
                case TOWER:
                    matches = obj.configTower != null && obj.configTower.getId() == configId;
                    break;
                case STANDALONE_BMS:
                    matches = obj.configBms != null && obj.configBms.getId() == configId;
                    break;
            }
            if (matches) {
                toRemove = obj;
                break;
            }
        }
        
        if (toRemove != null) {
            // Remove from scene
            if (toRemove.anchorNode != null) {
                arSceneView.getScene().removeChild(toRemove.anchorNode);
            }
            placedObjects.remove(toRemove);
            
            // Update counts
            updatePlacedCounts(toRemove, false);
            
            updateButtonVisibility();
        }
    }
    
    /**
     * Update button visibility
     */
    public void updateButtonVisibility() {
        if (buttonVisibilityCallback != null) {
            buttonVisibilityCallback.onControlButtonVisibilityChanged(hasPreview());
            buttonVisibilityCallback.onUndoButtonVisibilityChanged(!placedObjects.isEmpty());
            buttonVisibilityCallback.onResetButtonVisibilityChanged(!placedObjects.isEmpty());
        }
    }
    
    public void setButtonVisibilityCallback(ButtonVisibilityCallback callback) {
        this.buttonVisibilityCallback = callback;
    }
    
    /**
     * Check if object intersects with any placed objects
     */
    private boolean checkIntersection(ArObject newObject) {
        if (newObject.anchorNode == null) {
            return false;
        }
        
        Vector3 newPos = newObject.anchorNode.getWorldPosition();
        float newWidth = getObjectWidth(newObject);
        float newDepth = getObjectDepth(newObject);
        float newHeight = getObjectHeight(newObject);
        
        for (ArObject existing : placedObjects) {
            if (existing.anchorNode == null) {
                continue;
            }
            
            Vector3 existingPos = existing.anchorNode.getWorldPosition();
            float existingWidth = getObjectWidth(existing);
            float existingDepth = getObjectDepth(existing);
            float existingHeight = getObjectHeight(existing);
            
            // Check if bounding boxes intersect
            if (Math.abs(newPos.x - existingPos.x) < (newWidth + existingWidth) / 2f &&
                Math.abs(newPos.z - existingPos.z) < (newDepth + existingDepth) / 2f &&
                Math.abs(newPos.y - existingPos.y) < (newHeight + existingHeight) / 2f) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get object width in meters
     */
    private float getObjectWidth(ArObject object) {
        switch (object.type) {
            case INVERTER:
                return (float) (object.inverterItem.getWidth() / 1000.0);
            case TOWER:
                return (float) (object.batteryItem.getWidth() / 1000.0);
            case STANDALONE_BMS:
                return (float) (object.bmsItem.getWidth() / 1000.0);
            default:
                return 0.1f;
        }
    }
    
    /**
     * Get object depth in meters
     */
    private float getObjectDepth(ArObject object) {
        switch (object.type) {
            case INVERTER:
                return (float) (object.inverterItem.getDepth() / 1000.0);
            case TOWER:
                return (float) (object.batteryItem.getDepth() / 1000.0);
            case STANDALONE_BMS:
                return (float) (object.bmsItem.getDepth() / 1000.0);
            default:
                return 0.1f;
        }
    }
    
    /**
     * Get object height in meters
     */
    private float getObjectHeight(ArObject object) {
        switch (object.type) {
            case INVERTER:
                return (float) (object.inverterItem.getHeight() / 1000.0);
            case TOWER:
                // Tower height = battery height * count + BMS height if present
                float batteryHeight = (float) (object.batteryItem.getHeight() / 1000.0);
                int batteryCount = object.configTower != null ? object.configTower.getBatteriesCount() : 0;
                float totalHeight = batteryHeight * batteryCount;
                if (object.configBms != null && object.bmsItem != null) {
                    totalHeight += (float) (object.bmsItem.getHeight() / 1000.0);
                }
                return totalHeight;
            case STANDALONE_BMS:
                return (float) (object.bmsItem.getHeight() / 1000.0);
            default:
                return 0.1f;
        }
    }
    
    // Callback for error messages
    public interface ErrorCallback {
        void onPlacementError(String message);
    }
    
    private ErrorCallback errorCallback;
    
    public void setErrorCallback(ErrorCallback callback) {
        this.errorCallback = callback;
    }
    
    /**
     * Show labels for placed object
     */
    private void showLabels(ArObject object) {
        // Re-create labels now that object is placed
        // Labels will be added to rootNode in their thenAccept callbacks
        switch (object.type) {
            case INVERTER:
                if (object.inverterItem != null && object.configInverter != null) {
                    createInverterLabel(object, object.inverterItem, object.configInverter);
                }
                break;
            case TOWER:
                if (object.batteryItem != null) {
                    createTowerLabel(object, object.batteryItem);
                }
                break;
            case STANDALONE_BMS:
                if (object.bmsItem != null) {
                    createBmsLabel(object, object.bmsItem);
                }
                break;
        }
    }
    
    /**
     * Update object material to be semi-transparent
     */
    private void updateObjectMaterial(ArObject object) {
        // Update all visual nodes with appropriate colors (semi-transparent so text is visible)
        int nodeIndex = 0;
        for (VisualNodeInfo visualNodeInfo : object.visualNodes) {
            Color color;
            
            // For towers, check if this is a BMS node (red) or battery module (green)
            if (object.type == ObjectType.TOWER && object.configBms != null && object.bmsItem != null) {
                // Count battery modules
                int batteryModuleCount = object.configTower != null ? object.configTower.getBatteriesCount() : 0;
                // BMS is the last node after all battery modules
                if (nodeIndex >= batteryModuleCount) {
                    color = new Color(1.0f, 0.2f, 0.2f, 0.5f); // Red for BMS, semi-transparent
                } else {
                    color = new Color(0.2f, 0.8f, 0.2f, 0.5f); // Green for battery modules, semi-transparent
                }
            } else {
                // For other object types, use standard colors (semi-transparent)
                switch (object.type) {
                    case INVERTER:
                        color = new Color(0.2f, 0.4f, 1.0f, 0.5f); // Blue, semi-transparent
                        break;
                    case TOWER:
                        color = new Color(0.2f, 0.8f, 0.2f, 0.5f); // Green, semi-transparent
                        break;
                    case STANDALONE_BMS:
                        color = new Color(1.0f, 0.2f, 0.2f, 0.5f); // Red, semi-transparent
                        break;
                    default:
                        nodeIndex++;
                        continue;
                }
            }
            
            final int finalNodeIndex = nodeIndex;
            MaterialFactory.makeTransparentWithColor(context, color)
                    .thenAccept(material -> {
                        VisualNodeInfo nodeInfo = object.visualNodes.get(finalNodeIndex);
                        if (nodeInfo.node.getRenderable() != null) {
                            nodeInfo.node.setRenderable(
                                ShapeFactory.makeCube(
                                    nodeInfo.size,
                                    Vector3.zero(),
                                    material
                                )
                            );
                            // Restore position
                            nodeInfo.node.setLocalPosition(nodeInfo.position);
                        }
                    });
            nodeIndex++;
        }
    }
    
    /**
     * Remove preview object
     */
    public void removePreview() {
        if (currentPreviewObject != null) {
            if (currentPreviewObject.anchorNode != null) {
                arSceneView.getScene().removeChild(currentPreviewObject.anchorNode);
            }
            currentPreviewObject = null;
            updateButtonVisibility();
        }
    }
    
    /**
     * Handle object manipulation (called from ArDesignActivity)
     */
    public boolean handleObjectManipulation(MotionEvent event) {
        if (placedObjects.isEmpty() || currentPreviewObject != null) {
            return false;
        }
        
        int action = event.getActionMasked();
        
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (event.getPointerCount() == 1) {
                    // Single touch - select object (simplified - select last placed)
                    if (!placedObjects.isEmpty()) {
                        selectedObject = placedObjects.get(placedObjects.size() - 1);
                        isManipulating = true;
                        lastTouchX = event.getX();
                        lastTouchY = event.getY();
                        return true;
                    }
                }
                break;
                
            case MotionEvent.ACTION_MOVE:
                if (isManipulating && selectedObject != null) {
                    if (event.getPointerCount() == 1) {
                        // Single touch - move object
                        float deltaX = event.getX() - lastTouchX;
                        float deltaY = event.getY() - lastTouchY;
                        
                        // Convert screen delta to world movement
                        moveObjectOnPlane(selectedObject, deltaX, deltaY);
                        
                        lastTouchX = event.getX();
                        lastTouchY = event.getY();
                        return true;
                    } else if (event.getPointerCount() == 2) {
                        // Two touches - rotate object
                        float rotation = getRotation(event);
                        
                        if (initialRotation == 0f) {
                            initialRotation = rotation;
                            lastRotation = selectedObject.lastRotation;
                        } else {
                            float deltaRotation = rotation - initialRotation;
                            // Reduce sensitivity by dividing by a factor
                            float sensitivity = 0.5f; // Lower value = less sensitive
                            float newRotation = lastRotation + (deltaRotation * sensitivity);
                            selectedObject.lastRotation = newRotation;
                            selectedObject.rootNode.setLocalRotation(
                                Quaternion.axisAngle(new Vector3(0f, 1f, 0f), newRotation)
                            );
                            lastRotation = newRotation;
                        }
                        return true;
                    }
                }
                break;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                isManipulating = false;
                initialRotation = 0f;
                selectedObject = null;
                break;
        }
        
        return false;
    }
    
    /**
     * Move object on plane
     */
    private void moveObjectOnPlane(ArObject object, float deltaX, float deltaY) {
        if (object.anchorNode == null || arSession == null) {
            return;
        }
        
        try {
            Frame frame = arSession.update();
            if (frame == null) {
                return;
            }
            
            // Convert screen delta to world movement (inverted for natural feel)
            float moveScale = 0.001f; // Adjust sensitivity
            Vector3 moveDelta = new Vector3(
                -deltaX * moveScale,
                0f,
                deltaY * moveScale
            );
            
            // Get current position
            Vector3 currentPos = object.anchorNode.getWorldPosition();
            Vector3 newPos = Vector3.add(currentPos, moveDelta);
            
            // Keep Y at base level
            newPos.y = object.baseY;
            
            // Check for intersection before moving
            Vector3 originalPos = currentPos;
            object.anchorNode.setWorldPosition(newPos);
            
            // Temporarily check intersection
            boolean intersects = false;
            for (ArObject other : placedObjects) {
                if (other != object && other.anchorNode != null) {
                    if (checkObjectsIntersect(object, other)) {
                        intersects = true;
                        break;
                    }
                }
            }
            
            if (intersects) {
                // Revert position
                object.anchorNode.setWorldPosition(originalPos);
                if (errorCallback != null) {
                    errorCallback.onPlacementError("Invalid placement: Objects are overlapping");
                }
                return;
            }
            
            // Create new anchor at new position
            float[] translation = new float[]{newPos.x, newPos.y, newPos.z};
            Quaternion worldRotation = object.anchorNode.getWorldRotation();
            float[] rotation = new float[4];
            rotation[0] = worldRotation.x;
            rotation[1] = worldRotation.y;
            rotation[2] = worldRotation.z;
            rotation[3] = worldRotation.w;
            Pose newPose = new Pose(translation, rotation);
            
            Anchor newAnchor = arSession.createAnchor(newPose);
            object.anchorNode.setAnchor(newAnchor);
            
        } catch (Exception e) {
            Log.e(TAG, "Error moving object", e);
        }
    }
    
    /**
     * Check if two objects intersect
     */
    private boolean checkObjectsIntersect(ArObject obj1, ArObject obj2) {
        if (obj1.anchorNode == null || obj2.anchorNode == null) {
            return false;
        }
        
        Vector3 pos1 = obj1.anchorNode.getWorldPosition();
        Vector3 pos2 = obj2.anchorNode.getWorldPosition();
        
        float width1 = getObjectWidth(obj1);
        float depth1 = getObjectDepth(obj1);
        float height1 = getObjectHeight(obj1);
        
        float width2 = getObjectWidth(obj2);
        float depth2 = getObjectDepth(obj2);
        float height2 = getObjectHeight(obj2);
        
        return Math.abs(pos1.x - pos2.x) < (width1 + width2) / 2f &&
               Math.abs(pos1.z - pos2.z) < (depth1 + depth2) / 2f &&
               Math.abs(pos1.y - pos2.y) < (height1 + height2) / 2f;
    }
    
    /**
     * Rotate object
     */
    private void rotateObject(ArObject object, float deltaRotation) {
        if (object.rootNode == null) {
            return;
        }
        
        float newRotation = object.lastRotation + deltaRotation;
        object.rootNode.setLocalRotation(
            Quaternion.axisAngle(new Vector3(0f, 1f, 0f), newRotation)
        );
        object.lastRotation = newRotation;
    }
    
    /**
     * Get distance between two touch points
     */
    private float getDistance(MotionEvent event) {
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
    
    /**
     * Get rotation angle between two touch points
     */
    private float getRotation(MotionEvent event) {
        double deltaX = event.getX(0) - event.getX(1);
        double deltaY = event.getY(0) - event.getY(1);
        return (float) Math.toDegrees(Math.atan2(deltaY, deltaX));
    }
    
    /**
     * Vibrate
     */
    private void vibrate() {
        if (vibrator != null && vibrator.hasVibrator()) {
            long duration = 50;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(duration);
            }
        }
    }
    
    /**
     * Check if there is a preview object
     */
    public boolean hasPreview() {
        return currentPreviewObject != null;
    }
    
    /**
     * Check if currently manipulating an object
     */
    public boolean isManipulating() {
        return isManipulating;
    }
    
    /**
     * Get placed counts for filtering dialog
     */
    public java.util.Map<Integer, Integer> getPlacedInverterCounts() {
        return new java.util.HashMap<>(placedInverterCounts);
    }
    
    public java.util.Map<Integer, Integer> getPlacedTowerCounts() {
        return new java.util.HashMap<>(placedTowerCounts);
    }
    
    public java.util.Map<Integer, Integer> getPlacedBmsCounts() {
        return new java.util.HashMap<>(placedBmsCounts);
    }
    
    /**
     * Clear all objects
     */
    public void clearAll() {
        removePreview();
        for (ArObject object : placedObjects) {
            if (object.anchorNode != null) {
                arSceneView.getScene().removeChild(object.anchorNode);
            }
        }
        placedObjects.clear();
        placedInverterCounts.clear();
        placedTowerCounts.clear();
        placedBmsCounts.clear();
        lastPlacedObject = null;
        updateButtonVisibility();
        vibrate();
    }
}

