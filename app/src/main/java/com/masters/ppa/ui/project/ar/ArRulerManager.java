package com.masters.ppa.ui.project.ar;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.Build;
import android.util.Log;
import android.view.View;
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
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.masters.ppa.R;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Manages AR ruler functionality - placing points, drawing lines, measuring distances
 */
public class ArRulerManager {
    
    private static final String TAG = "ArRulerManager";
    
    private Context context;
    private ArSceneView arSceneView;
    private Session arSession;
    private TextView distanceTextView;
    private Vibrator vibrator;
    
    /**
     * Represents a measurement line segment
     */
    private static class MeasurementLine {
        Node lineNode;
        ArRulerLabelNode labelNode;
        int startPointIndex;
        int endPointIndex;
        boolean usesNewEndPoint;  // true if end point was newly created for this line
        boolean usesNewStartPoint;  // true if start point was newly created for this line
        
        MeasurementLine(Node lineNode, ArRulerLabelNode labelNode, int startPointIndex, int endPointIndex,
                        boolean usesNewEndPoint, boolean usesNewStartPoint) {
            this.lineNode = lineNode;
            this.labelNode = labelNode;
            this.startPointIndex = startPointIndex;
            this.endPointIndex = endPointIndex;
            this.usesNewEndPoint = usesNewEndPoint;
            this.usesNewStartPoint = usesNewStartPoint;
        }
    }
    
    // Measurement data
    private List<MeasurementPoint> measurementPoints = new ArrayList<>();
    private List<AnchorNode> anchorNodes = new ArrayList<>();
    private List<MeasurementLine> measurementLines = new ArrayList<>();
    
    // Preview line for next measurement
    private Node previewLineNode;
    private ArRulerLabelNode previewLabelNode;
    private Node previewLabelParentNode;
    private boolean isCreatingPreviewLabel = false;
    
    // Chain management - tracks where measurement chains are broken
    private int lastChainBreakIndex = -1;
    private AnchorNode currentLastAnchor;
    
    // Prevent multiple rapid calls
    private boolean isProcessingPlacement = false;
    
    // Prevent automatic segment creation during camera movement
    // Track last position to detect camera movement
    private Vector3 lastPreviewHitPosition = null;
    private static final float MIN_DISTANCE_FOR_PREVIEW_UPDATE = 0.01f; // 1 cm minimum movement
    
    // Callbacks
    public interface RulerStateListener {
        void onOkButtonVisibilityChanged(boolean visible);
        void onUndoButtonVisibilityChanged(boolean visible);
        void onPointsCountChanged(int count);
    }
    
    private RulerStateListener stateListener;
    
    public ArRulerManager(Context context, ArSceneView arSceneView, Session arSession, TextView distanceTextView) {
        this.context = context;
        this.arSceneView = arSceneView;
        this.arSession = arSession;
        this.distanceTextView = distanceTextView;
        
        // Initialize vibrator
        if (context != null) {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
    }
    
    public void setStateListener(RulerStateListener listener) {
        this.stateListener = listener;
    }
    
    /**
     * Handle tap to place a measurement point
     */
    public boolean handlePointPlacement() {
        if (arSceneView == null || arSession == null) {
            return false;
        }
        
        // Prevent multiple rapid calls
        if (isProcessingPlacement) {
            return false;
        }
        
        isProcessingPlacement = true;
        
        try {
            Frame frame = arSession.update();
            if (frame == null) {
                return false;
            }
            
            // Get hit result at center of screen
            float[] hitTestArray = new float[2];
            hitTestArray[0] = arSceneView.getWidth() / 2f;
            hitTestArray[1] = arSceneView.getHeight() / 2f;
            
            List<HitResult> hitResults = frame.hitTest(hitTestArray[0], hitTestArray[1]);
            
            if (!hitResults.isEmpty()) {
                HitResult hitResult = hitResults.get(0);
                Trackable trackable = hitResult.getTrackable();
                
                // Only allow placing points on planes (surfaces), not in air
                if (trackable instanceof Plane) {
                    Plane plane = (Plane) trackable;
                    if (plane.getTrackingState() == com.google.ar.core.TrackingState.TRACKING) {
                        // Check plane type - allow both horizontal and vertical
                        Plane.Type planeType = plane.getType();
                        if (planeType == Plane.Type.HORIZONTAL_UPWARD_FACING || 
                            planeType == Plane.Type.VERTICAL) {
                            
                            // Get hit position
                            com.google.ar.core.Pose hitPose = hitResult.getHitPose();
                            Vector3 hitPosition = new Vector3(hitPose.tx(), hitPose.ty(), hitPose.tz());
                            
                            // Check if there's an existing point nearby
                            AnchorNode existingNode = findNearbyPoint(hitPosition);
                            
                            if (existingNode != null) {
                                // Use existing point - if currentLastAnchor is null, start new segment from this point
                                // If currentLastAnchor is not null, connect from it to this existing point
                                useExistingPoint(existingNode);
                                isProcessingPlacement = false;
                                return true;
                            } else {
                                // Create new point
                                Anchor anchor;
                                
                                // Check if we're creating a vertical segment (from horizontal plane up/down)
                                if (currentLastAnchor != null && anchorNodes.contains(currentLastAnchor)) {
                                    Vector3 startPosition = currentLastAnchor.getWorldPosition();
                                    hitPosition = new Vector3(hitPose.tx(), hitPose.ty(), hitPose.tz());
                                    
                                    // Calculate differences
                                    float deltaX = Math.abs(hitPosition.x - startPosition.x);
                                    float deltaY = Math.abs(hitPosition.y - startPosition.y);
                                    float deltaZ = Math.abs(hitPosition.z - startPosition.z);
                                    
                                    // Check if this is a vertical segment:
                                    // - Significant vertical movement (deltaY > threshold)
                                    // - Small horizontal movement (deltaX and deltaZ are small)
                                    // - Original point is on horizontal plane
                                    float verticalThreshold = 0.1f; // 10 cm
                                    float horizontalThreshold = 0.15f; // 15 cm
                                    
                                    boolean isVerticalSegment = deltaY > verticalThreshold && 
                                                                  deltaX < horizontalThreshold && 
                                                                  deltaZ < horizontalThreshold;
                                    
                                    if (isVerticalSegment) {
                                        // For vertical line: point should be placed at the end of vertical line
                                        // Line goes vertically from startPosition to cursor height
                                        // End of vertical line is at (startPosition.x, hitPosition.y, startPosition.z)
                                        float[] translation = new float[3];
                                        translation[0] = startPosition.x;
                                        translation[1] = hitPosition.y; // Place point at cursor height (end of vertical line)
                                        translation[2] = startPosition.z;
                                        
                                        // Create pose with modified position (keep original rotation)
                                        float[] rotation = new float[4];
                                        hitPose.getRotationQuaternion(rotation, 0);
                                        Pose modifiedPose = new Pose(translation, rotation);
                                        
                                        // Create anchor with modified pose
                                        anchor = arSession.createAnchor(modifiedPose);
                                    } else {
                                        // Normal placement - use hit result as is
                                        anchor = hitResult.createAnchor();
                                    }
                                } else {
                                    // No previous point - normal placement
                                    anchor = hitResult.createAnchor();
                                }
                                
                                addMeasurementPoint(anchor);
                                isProcessingPlacement = false;
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling point placement", e);
        } finally {
            isProcessingPlacement = false;
        }
        
        return false;
    }
    
    /**
     * Find an existing point near the given position
     * @param position The position to check
     * @return AnchorNode of nearby point, or null if none found
     */
    private AnchorNode findNearbyPoint(Vector3 position) {
        // Threshold distance in meters (5 cm)
        float thresholdDistance = 0.05f;
        
        for (AnchorNode node : anchorNodes) {
            Vector3 nodePosition = node.getWorldPosition();
            Vector3 difference = Vector3.subtract(position, nodePosition);
            float distance = difference.length();
            
            if (distance < thresholdDistance) {
                return node;
            }
        }
        
        return null;
    }
    
    /**
     * Use an existing point instead of creating a new one
     */
    private void useExistingPoint(AnchorNode existingNode) {
        // Find the index of this node
        int existingIndex = anchorNodes.indexOf(existingNode);
        if (existingIndex == -1) {
            Log.w(TAG, "Existing node not found in anchorNodes list");
            return;
        }
        
        // Remove preview line
        removePreviewLine();
        
        // Hide distance text
        if (distanceTextView != null) {
            distanceTextView.setVisibility(View.GONE);
        }
        
        // Check if we can continue from current last anchor
        if (currentLastAnchor != null && anchorNodes.contains(currentLastAnchor)) {
            // Find the index of current last anchor
            int currentLastIndex = anchorNodes.indexOf(currentLastAnchor);
            if (currentLastIndex != -1 && currentLastIndex != existingIndex) {
                // Check if current last anchor is after last chain break
                if (currentLastIndex > lastChainBreakIndex) {
                    // Draw line from current last anchor to existing point
                    if (currentLastIndex < measurementPoints.size() && existingIndex < measurementPoints.size()) {
                        MeasurementPoint prevPoint = measurementPoints.get(currentLastIndex);
                        MeasurementPoint existingPoint = measurementPoints.get(existingIndex);
                        
                        // Start point is not new when connecting to existing point (it's an existing or previously created point)
                        createMeasurementLine(currentLastAnchor, existingNode, prevPoint, existingPoint, 
                                            currentLastIndex, existingIndex, false, false);
                        // After connecting to existing point, break the chain - same logic as OK button
                        breakMeasurementChain();
                    }
                }
            }
        } else {
            // No active segment - user clicked on an existing point to start a new segment from it
            // Just set it as currentLastAnchor, don't automatically connect to other points
            // User will place next point to create the segment
            // Start a new segment from the existing point
            currentLastAnchor = existingNode;
            // Set lastChainBreakIndex to allow connection from this point
            lastChainBreakIndex = existingIndex - 1;
        }
        
        updateOkButtonVisibility();
    }
    
    /**
     * Add a new measurement point
     */
    private void addMeasurementPoint(Anchor anchor) {
        MeasurementPoint point = new MeasurementPoint("", anchor, 0.0);
        measurementPoints.add(point);
        
        AnchorNode anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(arSceneView.getScene());
        anchorNodes.add(anchorNode);
        
        // Create acid yellow sphere for point
        MaterialFactory.makeOpaqueWithColor(context, new Color(1.0f, 1.0f, 0.0f))
                .thenAccept(material -> {
                    ModelRenderable sphere = ShapeFactory.makeSphere(0.01f, Vector3.zero(), material);
                    
                    // Make point face camera
                    ArRulerLabelNode arRulerLabelNode = new ArRulerLabelNode();
                    arRulerLabelNode.setParent(anchorNode);
                    arRulerLabelNode.setRenderable(sphere);
                });
        
        // Remove preview line when point is added
        removePreviewLine();
        
        // Reset last preview position when a point is added
        lastPreviewHitPosition = null;
        
        // Hide distance text when point is added
        if (distanceTextView != null) {
            distanceTextView.setVisibility(View.GONE);
        }
        
        // Draw line if we have a valid current last anchor to connect from
        // This handles the case when user starts a segment from an existing point
        if (currentLastAnchor != null && anchorNodes.contains(currentLastAnchor)) {
            int currentLastIndex = anchorNodes.indexOf(currentLastAnchor);
            
            if (currentLastIndex != -1 && currentLastIndex < measurementPoints.size() && 
                currentLastIndex < anchorNodes.size()) {
                // Check if current last anchor is after last chain break
                // Allow connection if currentLastIndex > lastChainBreakIndex OR if starting from existing point
                boolean canConnect = currentLastIndex > lastChainBreakIndex;
                if (canConnect) {
                    MeasurementPoint prevPoint = measurementPoints.get(currentLastIndex);
                    int lastPointIndex = measurementPoints.size() - 1;
                    if (lastPointIndex >= 0 && lastPointIndex < measurementPoints.size() &&
                        anchorNodes.size() > 0 && lastPointIndex < anchorNodes.size()) {
                        MeasurementPoint currPoint = measurementPoints.get(lastPointIndex);
                        AnchorNode currNode = anchorNodes.get(lastPointIndex);
                        
                        // Make sure we're not drawing a line to the same point
                        if (currentLastAnchor != currNode && currNode != null) {
                            // Check if start point was newly created
                            // A point is "new" if it was created after the last chain break
                            // This means it was created specifically for this segment chain
                            boolean startPointWasNew = (currentLastIndex > lastChainBreakIndex);
                            
                            // This is a new point, so mark line as using new point
                            createMeasurementLine(currentLastAnchor, currNode, prevPoint, currPoint, 
                                                currentLastIndex, lastPointIndex, true, startPointWasNew);
                        } else {
                            Log.w(TAG, "Cannot create line: currentLastAnchor == currNode or currNode is null");
                        }
                    } else {
                        Log.w(TAG, "Invalid lastPointIndex: " + lastPointIndex + 
                              ", measurementPoints.size()=" + measurementPoints.size() + 
                              ", anchorNodes.size()=" + anchorNodes.size());
                    }
                } else {
                }
            } else {
                Log.w(TAG, "Invalid currentLastIndex: " + currentLastIndex + 
                      ", measurementPoints.size()=" + measurementPoints.size() + 
                      ", anchorNodes.size()=" + anchorNodes.size());
                // Reset currentLastAnchor if it's invalid
                currentLastAnchor = null;
            }
        } else {
            if (currentLastAnchor == null) {
            } else if (!anchorNodes.contains(currentLastAnchor)) {
                Log.w(TAG, "addMeasurementPoint: currentLastAnchor not found in anchorNodes list - resetting");
                currentLastAnchor = null;
            }
            
            // If we have at least 2 points and no currentLastAnchor, try to connect the last two points
            // This handles the case after restart when currentLastAnchor might be null
            if (measurementPoints.size() >= 2 && currentLastAnchor == null) {
                // No current last anchor, but we have at least 2 points
                // Try to use previous point in list
                int prevIndex = measurementPoints.size() - 2;
                // Only draw line if previous point is not before last chain break
                if (prevIndex > lastChainBreakIndex && prevIndex >= 0 && prevIndex < anchorNodes.size()) {
                    MeasurementPoint prevPoint = measurementPoints.get(prevIndex);
                    MeasurementPoint currPoint = measurementPoints.get(measurementPoints.size() - 1);
                    AnchorNode prevNode = anchorNodes.get(prevIndex);
                    AnchorNode currNode = anchorNodes.get(anchorNodes.size() - 1);
                    
                    // Make sure we're not drawing a line to the same point
                    if (prevNode != currNode) {
                        // Check if start point was newly created
                        // A point is "new" if it was created after the last chain break
                        boolean startPointWasNew = (prevIndex > lastChainBreakIndex);
                        // This is a new point, so mark line as using new point
                        int currIndex = measurementPoints.size() - 1;
                        createMeasurementLine(prevNode, currNode, prevPoint, currPoint, 
                                            prevIndex, currIndex, true, startPointWasNew);
                    }
                }
            }
        }
        
        // Set the newly added point as the current last anchor
        currentLastAnchor = anchorNode;
        
        
        updateOkButtonVisibility();
    }
    
    /**
     * Create a measurement line between two anchors
     * @param startIndex Index of the start point in anchorNodes list
     * @param endIndex Index of the end point in anchorNodes list
     * @param usesNewPoint true if the end point is a newly created point, false if it's an existing point
     * @param usesNewStartPoint true if the start point was newly created, false if it's an existing point
     */
    private void createMeasurementLine(AnchorNode startNode, AnchorNode endNode, 
                                      MeasurementPoint startPoint, MeasurementPoint endPoint,
                                      int startIndex, int endIndex,
                                      boolean usesNewPoint, boolean usesNewStartPoint) {
        Vector3 startPos = startNode.getWorldPosition();
        Vector3 endPos = endNode.getWorldPosition();
        
        Vector3 difference = Vector3.subtract(endPos, startPos);
        float distance = difference.length();
        
        // Update length in point info
        endPoint.setLength((double) distance);
        
        // Calculate direction from start to end
        Vector3 direction = difference.normalized();
        Vector3 defaultZ = new Vector3(0f, 0f, 1f);
        Vector3 cross = Vector3.cross(defaultZ, direction);
        float dot = Vector3.dot(defaultZ, direction);
        
        Quaternion rotation;
        if (cross.length() < 0.001f) {
            // Vectors are parallel
            if (dot > 0.99f) {
                rotation = Quaternion.identity();
            } else {
                // Opposite direction - rotate 180 degrees around X axis
                rotation = Quaternion.axisAngle(new Vector3(1f, 0f, 0f), 180f);
            }
        } else {
            // Use lookRotation but with proper up vector
            Vector3 up = Vector3.cross(cross.normalized(), direction);
            if (up.length() < 0.001f) {
                up = Vector3.up();
            }
            up = up.normalized();
            rotation = Quaternion.lookRotation(direction, up);
            // Rotate 90 degrees around X to align cylinder's Z with direction
            Quaternion xRot = Quaternion.axisAngle(new Vector3(1f, 0f, 0f), -90f);
            rotation = Quaternion.multiply(rotation, xRot);
        }
        
        // Create line (cylinder) - thinner, acid yellow
        Quaternion finalRotation = rotation;
        // Make indices final for use in lambda
        final int finalStartIndex = startIndex;
        final int finalEndIndex = endIndex;
        MaterialFactory.makeOpaqueWithColor(context, new Color(1.0f, 1.0f, 0.0f))
                .thenAccept(material -> {
                    // Make cylinder slightly longer to ensure it reaches both points
                    float sphereRadius = 0.01f;
                    float lineLength = distance + (sphereRadius * 2);
                    ModelRenderable line = ShapeFactory.makeCylinder(0.002f, lineLength, Vector3.zero(), material);
                    
                    Node lineNode = new Node();
                    lineNode.setParent(startNode);
                    lineNode.setRenderable(line);
                    // Position at midpoint
                    Vector3 midPoint = Vector3.add(startPos, endPos).scaled(0.5f);
                    lineNode.setWorldPosition(midPoint);
                    lineNode.setWorldRotation(finalRotation);
                    
                    // Verify indices are correct
                    int verifiedStartIndex = finalStartIndex;
                    int verifiedEndIndex = finalEndIndex;
                    
                    if (verifiedStartIndex < 0 || verifiedStartIndex >= anchorNodes.size() || 
                        verifiedEndIndex < 0 || verifiedEndIndex >= anchorNodes.size()) {
                        Log.e(TAG, "Invalid indices: startIndex=" + verifiedStartIndex + ", endIndex=" + verifiedEndIndex + 
                              ", anchorNodes.size()=" + anchorNodes.size());
                        return;
                    }
                    
                    // Verify nodes match
                    int finalVerifiedStartIndex = verifiedStartIndex;
                    int finalVerifiedEndIndex = verifiedEndIndex;
                    
                    if (anchorNodes.get(finalVerifiedStartIndex) != startNode || anchorNodes.get(finalVerifiedEndIndex) != endNode) {
                        Log.e(TAG, "Node mismatch: startIndex=" + finalVerifiedStartIndex + ", endIndex=" + finalVerifiedEndIndex);
                        // Try to find correct indices
                        int foundStartIndex = anchorNodes.indexOf(startNode);
                        int foundEndIndex = anchorNodes.indexOf(endNode);
                        if (foundStartIndex != -1 && foundEndIndex != -1) {
                            finalVerifiedStartIndex = foundStartIndex;
                            finalVerifiedEndIndex = foundEndIndex;
                        } else {
                            Log.e(TAG, "Could not find correct indices, aborting line creation");
                            return;
                        }
                    }
                    
                    // Create final copies for use in nested lambda
                    final int finalStartIndexForLine = finalVerifiedStartIndex;
                    final int finalEndIndexForLine = finalVerifiedEndIndex;
                    
                    // Create text with distance
                    ViewRenderable.builder()
                            .setView(context, R.layout.ar_object_label)
                            .build()
                            .thenAccept(viewRenderable -> {
                                TextView textView = (TextView) viewRenderable.getView();
                                double distanceCm = distance * 100;
                                BigDecimal bd = new BigDecimal(distanceCm);
                                bd = bd.setScale(2, RoundingMode.HALF_UP);
                                textView.setText(String.format(Locale.getDefault(), "%.2f cm", bd.doubleValue()));
                                viewRenderable.setShadowCaster(false);
                                
                                ArRulerLabelNode textNode = new ArRulerLabelNode();
                                textNode.setParent(lineNode);
                                textNode.setLocalRotation(Quaternion.axisAngle(new Vector3(0f, 1f, 0f), 90f));
                                // Position text at midpoint of line (slightly above)
                                textNode.setLocalPosition(new Vector3(0f, 0.01f, 0f));
                                textNode.setRenderable(viewRenderable);
                                
                                // Create MeasurementLine object and add to list
                                MeasurementLine measurementLine = new MeasurementLine(
                                    lineNode, textNode, finalStartIndexForLine, finalEndIndexForLine, usesNewPoint, usesNewStartPoint);
                                measurementLines.add(measurementLine);
                            });
                });
    }
    
    /**
     * Break the measurement chain - next point won't be connected to previous
     */
    public void breakMeasurementChain() {
        // Break the chain - next point won't be connected to previous
        lastChainBreakIndex = measurementPoints.size() - 1;
        updateOkButtonVisibility();
        
        // Remove preview line
        removePreviewLine();
        currentLastAnchor = null;
        lastPreviewHitPosition = null;
        
        // Hide distance text
        if (distanceTextView != null) {
            distanceTextView.setVisibility(View.GONE);
        }
    }
    
    /**
     * Trigger light vibration
     */
    private void vibrateLight() {
        if (vibrator != null && vibrator.hasVibrator()) {
            long duration = 100; // Same duration for all vibrations
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(duration);
            }
        }
    }
    
    /**
     * Trigger strong vibration
     */
    private void vibrateStrong() {
        if (vibrator != null && vibrator.hasVibrator()) {
            long duration = 100; // Same duration for all vibrations
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(duration);
            }
        }
    }
    
    /**
     * Check if a point is used by any measurement lines
     * @param pointIndex Index of the point to check
     * @return true if the point is used by any lines, false otherwise
     */
    private boolean isPointUsedByAnyLine(int pointIndex) {
        if (pointIndex < 0 || pointIndex >= anchorNodes.size()) {
            return false;
        }
        
        // Check all measurement lines
        for (MeasurementLine line : measurementLines) {
            if (line != null) {
                // Check if point is used as start or end point
                if (line.startPointIndex == pointIndex || line.endPointIndex == pointIndex) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Helper method to remove a point at a specific index
     */
    private void removePointAtIndex(int pointIndex) {
        if (pointIndex < 0 || pointIndex >= anchorNodes.size() || pointIndex >= measurementPoints.size()) {
            Log.e(TAG, "Invalid point index: " + pointIndex);
            return;
        }
        
        // Remove the point
        AnchorNode nodeToRemove = anchorNodes.get(pointIndex);
        if (nodeToRemove != null) {
            try {
                // Remove all children first
                List<Node> children = new ArrayList<>();
                for (Node child : nodeToRemove.getChildren()) {
                    children.add(child);
                }
                for (Node child : children) {
                    nodeToRemove.removeChild(child);
                }
                
                // Remove node from scene
                if (nodeToRemove.getParent() != null) {
                    nodeToRemove.getParent().removeChild(nodeToRemove);
                } else if (arSceneView != null && arSceneView.getScene() != null) {
                    arSceneView.getScene().removeChild(nodeToRemove);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error removing node from scene", e);
            }
        }
        
        // Remove from lists
        measurementPoints.remove(pointIndex);
        anchorNodes.remove(pointIndex);
        
        // Update indices in all remaining lines
        updateLineIndicesAfterPointRemoval(pointIndex);
    }
    
    /**
     * Check for orphaned points and remove them
     */
    private void checkAndRemoveOrphanedPoints() {
        // Check for orphaned points from back to front to avoid index issues
        for (int i = anchorNodes.size() - 1; i >= 0; i--) {
            if (!isPointUsedByAnyLine(i)) {
                removePointAtIndex(i);
            }
        }
        
        // After removing orphaned points, break the chain
        currentLastAnchor = null;
        // Set lastChainBreakIndex to the last point index
        if (!anchorNodes.isEmpty()) {
            lastChainBreakIndex = anchorNodes.size() - 1;
        } else {
            lastChainBreakIndex = -1;
        }
    }
    
    /**
     * Update indices in all measurement lines after a point is removed
     * @param removedIndex Index of the removed point
     */
    private void updateLineIndicesAfterPointRemoval(int removedIndex) {
        // Update all line indices that reference points after the removed one
        for (MeasurementLine line : measurementLines) {
            if (line != null) {
                // If start point index is after removed point, decrement it
                if (line.startPointIndex > removedIndex) {
                    line.startPointIndex--;
                }
                // If end point index is after removed point, decrement it
                if (line.endPointIndex > removedIndex) {
                    line.endPointIndex--;
                }
            }
        }
    }
    
    /**
     * Delete the last measurement point or last line segment
     */
    public void removeLastPoint() {
        // Trigger light vibration when removing a point
        vibrateLight();

        // If there are lines, remove the last line segment
        if (!measurementLines.isEmpty()) {
            // Get the index of the line we're removing
            int lineIndexToRemove = measurementLines.size() - 1;

            // Remove last line
            MeasurementLine lineToRemove = measurementLines.remove(lineIndexToRemove);

            // Remove line node from scene
            if (lineToRemove.lineNode != null && lineToRemove.lineNode.getParent() != null) {
                lineToRemove.lineNode.getParent().removeChild(lineToRemove.lineNode);
            }

            // Remove label node from scene
            if (lineToRemove.labelNode != null && lineToRemove.labelNode.getParent() != null) {
                lineToRemove.labelNode.getParent().removeChild(lineToRemove.labelNode);
            }

            // Get line properties
            boolean usedNewPoint = lineToRemove.usesNewEndPoint;
            boolean usedNewStartPoint = lineToRemove.usesNewStartPoint;
            int startPointIndex = lineToRemove.startPointIndex;
            int endPointIndex = lineToRemove.endPointIndex;

            // Remove end point if it was newly created for this line
            // AND it's not used by any other line
            if (usedNewPoint && endPointIndex >= 0 && endPointIndex < anchorNodes.size() &&
                    endPointIndex < measurementPoints.size()) {
                
                // Check if this point is used by any other line
                boolean usedByOtherLines = false;
                for (MeasurementLine line : measurementLines) {
                    if (line.startPointIndex == endPointIndex || line.endPointIndex == endPointIndex) {
                        usedByOtherLines = true;
                        break;
                    }
                }
                
                // Only remove if not used by other lines
                if (!usedByOtherLines) {
                    removePointAtIndex(endPointIndex);

                    // Adjust startPointIndex if needed
                    if (startPointIndex > endPointIndex) {
                        startPointIndex--;
                    }
                }
            }

            // Remove start point if it was newly created for this line
            // AND it's not used by any other line
            if (usedNewStartPoint && startPointIndex >= 0 && startPointIndex < anchorNodes.size() &&
                    startPointIndex < measurementPoints.size()) {
                
                // Check if this point is used by any other line
                boolean usedByOtherLines = false;
                for (MeasurementLine line : measurementLines) {
                    if (line.startPointIndex == startPointIndex || line.endPointIndex == startPointIndex) {
                        usedByOtherLines = true;
                        break;
                    }
                }
                
                // Only remove if not used by other lines
                if (!usedByOtherLines) {
                    removePointAtIndex(startPointIndex);
                }
            }
        }
    
            
            // After undo, break the chain - don't automatically connect to the next point
            // This simulates pressing the "OK" button
            currentLastAnchor = null;
            // Set lastChainBreakIndex to the last point index
            if (!anchorNodes.isEmpty()) {
                lastChainBreakIndex = anchorNodes.size() - 1;
            } else {
                lastChainBreakIndex = -1;
            }
            Log.d(TAG, "Breaking chain after undo: currentLastAnchor=null, lastChainBreakIndex=" + lastChainBreakIndex);
            
            // Check for orphaned points after removing a line
            checkAndRemoveOrphanedPoints();

            // No lines, check for orphaned points
            checkAndRemoveOrphanedPoints();
        
        
        // Log state after removing point
        Log.d(TAG, "removeLastPoint: After removal: " +
              "measurementPoints.size()=" + measurementPoints.size() + 
              ", anchorNodes.size()=" + anchorNodes.size() + 
              ", measurementLines.size()=" + measurementLines.size() + 
              ", currentLastAnchor=" + (currentLastAnchor != null ? "set" : "null") +
              ", lastChainBreakIndex=" + lastChainBreakIndex);
        
        updateOkButtonVisibility();
    }

    
    /**
     * Clear all measurement points and lines
     */
    public void clearAllMeasurements() {
        try {
            // Log state before clearing
            Log.d(TAG, "clearAllMeasurements: Before clearing: " +
                  "measurementPoints.size()=" + measurementPoints.size() + 
                  ", anchorNodes.size()=" + anchorNodes.size() + 
                  ", measurementLines.size()=" + measurementLines.size() + 
                  ", currentLastAnchor=" + (currentLastAnchor != null ? "set" : "null") +
                  ", lastChainBreakIndex=" + lastChainBreakIndex);
            
            // Trigger strong vibration when clearing all
            vibrateStrong();
            
            // Clear all anchor nodes (points) - need to remove from scene first
            // Remove in reverse order to avoid index issues
            for (int i = anchorNodes.size() - 1; i >= 0; i--) {
                try {
                    AnchorNode node = anchorNodes.get(i);
                    if (node != null) {
                        // Remove all children first
                        List<Node> children = new ArrayList<>();
                        for (Node child : node.getChildren()) {
                            children.add(child);
                        }
                        for (Node child : children) {
                            try {
                                node.removeChild(child);
                            } catch (Exception e) {
                                Log.e(TAG, "Error removing child from anchor node", e);
                            }
                        }
                        
                        // Remove node from scene - try multiple methods to ensure it's removed
                        boolean removed = false;
                        if (node.getParent() != null) {
                            try {
                                node.getParent().removeChild(node);
                                removed = true;
                            } catch (Exception e) {
                                Log.e(TAG, "Error removing anchor node from parent", e);
                            }
                        }
                        if (!removed && arSceneView != null && arSceneView.getScene() != null) {
                            try {
                                // Try to remove from scene directly
                                Scene scene = arSceneView.getScene();
                                if (scene != null) {
                                    scene.removeChild(node);
                                    removed = true;
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error removing anchor node from scene", e);
                            }
                        }
                        if (!removed) {
                            Log.w(TAG, "Could not remove anchor node at index " + i + " from scene");
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error removing anchor node at index " + i, e);
                }
            }
            measurementPoints.clear();
            anchorNodes.clear();
            
            System.gc();
            
            List<MeasurementLine> linesToRemove = new ArrayList<>(measurementLines);
            for (MeasurementLine line : linesToRemove) {
                try {
                    if (line.labelNode != null) {
                        try {
                            if (line.labelNode.getParent() != null) {
                                line.labelNode.getParent().removeChild(line.labelNode);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error removing label node", e);
                        }
                    }
                    // Remove line node
                    if (line.lineNode != null) {
                        try {
                            List<Node> children = new ArrayList<>();
                            for (Node child : line.lineNode.getChildren()) {
                                children.add(child);
                            }
                            for (Node child : children) {
                                try {
                                    line.lineNode.removeChild(child);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error removing child from line node", e);
                                }
                            }
                            
                            if (line.lineNode.getParent() != null) {
                                line.lineNode.getParent().removeChild(line.lineNode);
                            } else if (arSceneView != null && arSceneView.getScene() != null) {
                                arSceneView.getScene().removeChild(line.lineNode);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error removing line node", e);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error removing line", e);
                }
            }
            measurementLines.clear();
            
            removePreviewLine();
            
            currentLastAnchor = null;
            lastChainBreakIndex = -1;
            lastPreviewHitPosition = null;
            
            
            updateOkButtonVisibility();
        } catch (Exception e) {
            Log.e(TAG, "Error clearing all measurements", e);
        }
    }
    
    /**
     * Update preview line from last point to crosshair position
     * IMPORTANT: This method only updates the PREVIEW line, it never creates actual measurement segments.
     * Actual segments are only created when handlePointPlacement() is explicitly called via button press.
     */
    public void updatePreviewLine(FrameTime frameTime) {
        if (arSession == null) {
            if (distanceTextView != null) {
                distanceTextView.setVisibility(View.GONE);
            }
            return;
        }
        
        // If processing placement (actual point creation), don't update preview
        // This ensures preview doesn't interfere with actual point placement
        if (isProcessingPlacement) {
            return;
        }
        
        // If no current last anchor, don't show preview
        if (currentLastAnchor == null) {
            if (distanceTextView != null) {
                distanceTextView.setVisibility(View.GONE);
            }
            lastPreviewHitPosition = null;
            return;
        }
        
        // Check if last anchor is after last chain break
        int lastAnchorIndex = anchorNodes.indexOf(currentLastAnchor);
        if (lastAnchorIndex <= lastChainBreakIndex) {
            if (distanceTextView != null) {
                distanceTextView.setVisibility(View.GONE);
            }
            lastPreviewHitPosition = null;
            return;
        }
        
        try {
            Frame frame = arSession.update();
            if (frame == null) {
                return;
            }
            
            // Get hit result at center of screen for preview
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
                        // Check plane type - allow both horizontal and vertical
                        Plane.Type planeType = plane.getType();
                        if (planeType == Plane.Type.HORIZONTAL_UPWARD_FACING || 
                            planeType == Plane.Type.VERTICAL) {
                            com.google.ar.core.Pose hitPose = hitResult.getHitPose();
                            Vector3 hitPosition = new Vector3(hitPose.tx(), hitPose.ty(), hitPose.tz());
                            // Only update preview visualization - never create actual segments here
                            updatePreviewLineVisualization(hitPosition);
                        }
                    }
                }
            } else {
                // Hide distance text if no hit result
                if (distanceTextView != null) {
                    distanceTextView.setVisibility(View.GONE);
                }
                // Don't reset lastPreviewHitPosition here - keep it for smoother transitions
            }
        } catch (Exception e) {
            // Ignore errors during frame update
            Log.w(TAG, "Error in updatePreviewLine", e);
        }
    }
    
    /**
     * Update preview line visualization
     */
    private void updatePreviewLineVisualization(Vector3 hitPosition) {
        if (currentLastAnchor == null) {
            return;
        }
        
        Vector3 lastPos = currentLastAnchor.getWorldPosition();
        
        // Check if this is a vertical segment for preview
        float deltaX = Math.abs(hitPosition.x - lastPos.x);
        float deltaY = Math.abs(hitPosition.y - lastPos.y);
        float deltaZ = Math.abs(hitPosition.z - lastPos.z);
        
        float verticalThreshold = 0.1f; // 10 cm
        float horizontalThreshold = 0.15f; // 15 cm
        
        boolean isVerticalSegment = deltaY > verticalThreshold && 
                                      deltaX < horizontalThreshold && 
                                      deltaZ < horizontalThreshold;
        
        // For vertical line, use end position at cursor height with X and Z from start point
        Vector3 endPosition;
        if (isVerticalSegment) {
            endPosition = new Vector3(lastPos.x, hitPosition.y, lastPos.z);
        } else {
            endPosition = hitPosition;
        }
        
        Vector3 difference = Vector3.subtract(endPosition, lastPos);
        float distance = difference.length();
        
        // Update distance text at top center
        if (distanceTextView != null) {
            double distanceCm = distance * 100;
            BigDecimal bd = new BigDecimal(distanceCm);
            bd = bd.setScale(2, RoundingMode.HALF_UP);
            distanceTextView.setText(String.format(Locale.getDefault(), "%.2f cm", bd.doubleValue()));
            distanceTextView.setVisibility(View.VISIBLE);
        }
        
        // Calculate direction from last point to hit position
        Vector3 direction = difference.normalized();
        Vector3 defaultZ = new Vector3(0f, 0f, 1f);
        Vector3 cross = Vector3.cross(defaultZ, direction);
        float dot = Vector3.dot(defaultZ, direction);
        
        Quaternion rotation;
        if (cross.length() < 0.001f) {
            if (dot > 0.99f) {
                rotation = Quaternion.identity();
            } else {
                rotation = Quaternion.axisAngle(new Vector3(1f, 0f, 0f), 180f);
            }
        } else {
            Vector3 up = Vector3.cross(cross.normalized(), direction);
            if (up.length() < 0.001f) {
                up = Vector3.up();
            }
            up = up.normalized();
            rotation = Quaternion.lookRotation(direction, up);
            Quaternion xRot = Quaternion.axisAngle(new Vector3(1f, 0f, 0f), -90f);
            rotation = Quaternion.multiply(rotation, xRot);
        }
        
        // Remove old preview line
        if (previewLineNode != null && previewLineNode.getParent() != null) {
            previewLineNode.getParent().removeChild(previewLineNode);
            previewLineNode = null;
        }
        
        // Remove old preview text nodes
        if (previewLabelNode != null && previewLabelNode.getParent() != null) {
            previewLabelNode.getParent().removeChild(previewLabelNode);
            previewLabelNode = null;
        }
        if (previewLabelParentNode != null && previewLabelParentNode.getParent() != null) {
            previewLabelParentNode.getParent().removeChild(previewLabelParentNode);
            previewLabelParentNode = null;
        }
        isCreatingPreviewLabel = false;
        
        // Create new preview line
        float sphereRadius = 0.01f;
        float lineLength = distance + (sphereRadius * 2);
        Quaternion finalRotation = rotation;
        MaterialFactory.makeOpaqueWithColor(context, new Color(1.0f, 1.0f, 0.0f, 0.5f))
                .thenAccept(material -> {
                    ModelRenderable line = ShapeFactory.makeCylinder(0.002f, lineLength, Vector3.zero(), material);
                    
                    previewLineNode = new Node();
                    previewLineNode.setParent(currentLastAnchor);
                    previewLineNode.setRenderable(line);
                    // Position at midpoint
                    Vector3 midPoint = Vector3.add(lastPos, endPosition).scaled(0.5f);
                    previewLineNode.setWorldPosition(midPoint);
                    previewLineNode.setWorldRotation(finalRotation);
                });
    }
    
    /**
     * Remove preview line
     */
    private void removePreviewLine() {
        if (previewLineNode != null && previewLineNode.getParent() != null) {
            previewLineNode.getParent().removeChild(previewLineNode);
            previewLineNode = null;
        }
        if (previewLabelNode != null && previewLabelNode.getParent() != null) {
            previewLabelNode.getParent().removeChild(previewLabelNode);
            previewLabelNode = null;
        }
        if (previewLabelParentNode != null && previewLabelParentNode.getParent() != null) {
            previewLabelParentNode.getParent().removeChild(previewLabelParentNode);
            previewLabelParentNode = null;
        }
        isCreatingPreviewLabel = false;
        // Reset last preview position when preview is removed
        lastPreviewHitPosition = null;
    }
    
    /**
     * Update OK button visibility based on current state
     */
    public void updateOkButtonVisibility() {
        if (stateListener != null) {
            int pointsSinceLastBreak = measurementPoints.size() - (lastChainBreakIndex + 1);
            boolean shouldShow = pointsSinceLastBreak >= 2;
            stateListener.onOkButtonVisibilityChanged(shouldShow);
            // Also update undo button visibility - show if there are any lines or points
            stateListener.onUndoButtonVisibilityChanged(!measurementLines.isEmpty() || !measurementPoints.isEmpty());
        }
    }
    
    /**
     * Get current number of measurement points
     */
    public int getPointsCount() {
        return measurementPoints.size();
    }
    
    /**
     * Check if there are any measurement points
     */
    public boolean hasMeasurements() {
        return !measurementPoints.isEmpty();
    }
}

