// 
// Copyright 2000,2001,2002 The Regents of the University of California
// All Rights Reserved
// 
// Permission to use, copy, modify and distribute any part of this
// Walrus software package for educational, research and non-profit
// purposes, without fee, and without a written agreement is hereby
// granted, provided that the above copyright notice, this paragraph
// and the following paragraphs appear in all copies.
//   
// Those desiring to incorporate this into commercial products or use
// for commercial purposes should contact the Technology Transfer
// Office, University of California, San Diego, 9500 Gilman Drive, La
// Jolla, CA 92093-0910, Ph: (858) 534-5815, FAX: (858) 534-7345.
// 
// IN NO EVENT SHALL THE UNIVERSITY OF CALIFORNIA BE LIABLE TO ANY
// PARTY FOR DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL
// DAMAGES, INCLUDING LOST PROFITS, ARISING OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF THE UNIVERSITY OF CALIFORNIA HAS BEEN ADVISED OF
// THE POSSIBILITY OF SUCH DAMAGE.
//  
// THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE
// UNIVERSITY OF CALIFORNIA HAS NO OBLIGATION TO PROVIDE MAINTENANCE,
// SUPPORT, UPDATES, ENHANCEMENTS, OR MODIFICATIONS. THE UNIVERSITY
// OF CALIFORNIA MAKES NO REPRESENTATIONS AND EXTENDS NO WARRANTIES
// OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT LIMITED
// TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
// PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE
// ANY PATENT, TRADEMARK OR OTHER RIGHTS.
//  
// The Walrus software is developed by the Walrus Team at the
// University of California, San Diego under the Cooperative Association
// for Internet Data Analysis (CAIDA) Program.  Support for this effort
// is provided by NSF grant ANI-9814421, DARPA NGI Contract N66001-98-2-8922,
// Sun Microsystems, and CAIDA members.
// 

import java.util.*;
import javax.media.j3d.*;
import javax.vecmath.*;
import java.awt.Font;
import com.sun.j3d.utils.geometry.Text2D;

public class H3ViewParameters
{
    ////////////////////////////////////////////////////////////////////////
    // CONSTRUCTORS
    ////////////////////////////////////////////////////////////////////////
    
    public H3ViewParameters(H3Canvas3D canvas)
    {
	m_canvas = canvas;

	initializeBasicAppearances();
	initializeGraphAppearances();
	initializeGradedNodeAppearances();
	initializePickAppearances();

	m_pixelToMeterScale = computePixelToMeterScale(canvas.getScreen3D());
	m_pickRadius = PICK_RADIUS_PIXELS * m_pixelToMeterScale;
	m_pickEquivalenceRadius =
	    PICK_EQUIVALENCE_RADIUS_PIXELS * m_pixelToMeterScale;
	m_nodeRadius = NODE_RADIUS_PIXELS * m_pixelToMeterScale;

	m_pickViewer = new H3PickViewer(m_pickRadius);
    }

    ////////////////////////////////////////////////////////////////////////
    // PUBLIC METHODS
    ////////////////////////////////////////////////////////////////////////

    public void refresh()
    {
	m_canvas.getImagePlateToVworld(m_imageToVworld);

	m_pickViewer.setImageToVworldTransform(m_imageToVworld);
	m_nodeImage.setImageToVworldTransform(m_imageToVworld);
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - 

    // (x, y) of the lower-left corner of the label in image-plate coordinates
    //
    // For some unknown reason, successive labels appear *behind* previous
    // labels.  The parameter {zOffset} is meant to provide a hint about
    // the ordering of a set of labels put up during one 'labelling session'.
    // This parameter is used to calculate a small +z offset to apply to
    // the label.  The best way to generate it in the caller is to pass in
    // a counter, starting at zero, which increments by one as successive
    // labels are requested.  It should be reset to zero when some user
    // interaction causes all labels to be cleared.
    public void drawLabel
	(GraphicsContext3D gc, double x, double y, int zOffset, String s)
    {
	boolean frontBufferRenderingState = gc.getFrontBufferRendering();
	gc.setBufferOverride(true);
	gc.setFrontBufferRendering(true);

	Point3d eye = getEye();
	double labelZ = zOffset * LABEL_Z_OFFSET_SCALE
	    + LABEL_Z_SCALE * eye.z + LABEL_Z_OFFSET;

	double xOffset = LABEL_X_OFFSET * m_pixelToMeterScale;
	double yOffset = LABEL_Y_OFFSET * m_pixelToMeterScale;
	Point3d p = new Point3d(x + xOffset, y + yOffset, 0.0);
	{
	    // Project given (x, y) coordinates to the plane z=labelZ.

	    // Convert from image-plate to eye coordinates.
	    p.x -= eye.x;  p.y -= eye.y;

	    double inversePerspectiveScale = 1.0 - labelZ / eye.z;
	    p.x *= inversePerspectiveScale;
	    p.y *= inversePerspectiveScale;

	    // Convert from eye to image-plate coordinates.
	    p.x += eye.x;  p.y += eye.y;
	}

	Transform3D scale = new Transform3D();
	scale.set(LABEL_SCALE);

	Vector3d t = new Vector3d(p.x, p.y, labelZ);
	Transform3D translation = new Transform3D();
	translation.set(t);
	translation.mul(scale);

	Transform3D transform = new Transform3D(m_imageToVworld);
	transform.mul(translation);

	gc.setModelTransform(transform);

	// XXX: Courier may not be available on all systems.
	Text2D text = new Text2D(s, new Color3f(1.0f, 1.0f, 1.0f),
				 "Courier", 24, Font.BOLD);

	gc.draw(text);
	gc.flush(true);
	
	// NOTE: Resetting the model transform here is very important.
	//       For some reason, not doing this causes the immediate
	//       following frame to render incorrectly (but subsequent
	//       frames will render correctly).  In some ways, this
	//       makes sense, because most rendering code assumes that
	//       GraphicsContext3D has been set to some reasonable
	//       transform.
	gc.setModelTransform(m_objectTransform);
	gc.setFrontBufferRendering(frontBufferRenderingState);
    }

    // (x, y) in image-plate coordinates
    public void drawPickViewer(GraphicsContext3D gc, double x, double y)
    {
	m_pickViewer.draw(gc, x, y);
    }

    public void drawAxes(GraphicsContext3D gc)
    {
	if (m_axesEnabled)
	{
	    m_axes.draw(gc, m_objectTransform);
	}
    }

    public void setAxesEnabled(boolean enable)
    {
	m_axesEnabled = enable;
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - 

    public void installDepthCueing()
    {
	if (m_depthCueingEnabled && m_depthCueing == null)
	{
	    m_depthCueing = new LinearFog(0.0f, 0.0f, 0.0f);
	    m_depthCueing.setFrontDistance(DEPTH_CUEING_FRONT);
	    m_depthCueing.setBackDistance(DEPTH_CUEING_BACK);
	    m_depthCueing.setInfluencingBounds
		(new BoundingSphere(new Point3d(0.0, 0.0, 0.0), 100.0));
	}

	GraphicsContext3D gc = m_canvas.getGraphicsContext3D();

	// XXX: GraphicsContext3D.setFog(null) is throwing a
	//      NullPointerException contrary to the documentation.
	//      For the moment, therefore, there is no way to disable
	//      the fog once installed.
	if (m_depthCueingEnabled) gc.setFog(m_depthCueing);
    }

    public void setDepthCueingEnabled(boolean enable)
    {
	m_depthCueingEnabled = enable;
	if (!enable)
	{
	    m_depthCueing = null;
	}
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - 

    public void putModelTransform(GraphicsContext3D gc)
    {
	gc.setModelTransform(m_objectTransform);
    }

    public Transform3D getObjectToEyeTransform()
    {
	m_canvas.getCenterEyeInImagePlate(m_eye);
	m_imageToEye.set(new Vector3d(-m_eye.x, -m_eye.y, 0.0));
	m_canvas.getVworldToImagePlate(m_vworldToImage);

	Transform3D transform = new Transform3D(m_imageToEye);
	transform.mul(m_vworldToImage);
	transform.mul(m_objectTransform);
	return transform;
    }

    public Transform3D extendObjectTransform(Matrix4d t)
    {
	Transform3D transform = new Transform3D(t);
	transform.mul(m_objectTransform);
	m_objectTransform.set(transform);

	return m_objectTransform;
    }

    public Transform3D getObjectTransform()
    {
	return m_objectTransform;
    }

    public void setObjectTransform(Transform3D transform)
    {
	m_objectTransform.set(transform);
    }

    public void saveObjectTransform()
    {
	m_savedObjectTransform.set(m_objectTransform);
    }

    public void discardObjectTransform()
    {
        // Nothing needs to be done; simply not use m_savedObjectTransform.
    }

    public void restoreObjectTransform()
    {
	m_objectTransform.set(m_savedObjectTransform);
    }

    public void resetObjectTransform()
    {
	m_objectTransform.setIdentity();
    }

    public Point3d getEye()
    {
	m_canvas.getCenterEyeInImagePlate(m_eye);
	return m_eye;
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - 

    public H3PickViewer getPickViewer()
    {
	return m_pickViewer;
    }

    public H3Circle getNodeImage()
    {
	return m_nodeImage;
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - 

    public TransparencyAttributes getTransparencyAttributes()
    {
	return m_transparencyAttributes;
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - 

    // These two are used by the nonadaptive renderer.

    public Appearance getPointAppearance()
    {
	return m_pointAppearance;
    }

    public Appearance getLineAppearance()
    {
	return m_lineAppearance;
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - 

    // These three are used by the adaptive renderer.

    public Appearance getNodeAppearance()
    {
	return m_nodeAppearance;
    }

    public Appearance getTreeLinkAppearance()
    {
	return m_treeLinkAppearance;
    }

    public Appearance getNontreeLinkAppearance()
    {
	return m_nontreeLinkAppearance;
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - 

    // These three are used by the adaptive renderer.

    public Appearance getNearNodeAppearance()
    {
	return m_nearNodeAppearance;
    }

    public Appearance getMiddleNodeAppearance()
    {
	return m_middleNodeAppearance;
    }

    public Appearance getFarNodeAppearance()
    {
	return m_farNodeAppearance;
    }

    public Appearance getPickAppearance()
    {
	return m_pickAppearance;
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - 

    public double getNodeRadius()
    {
	return m_nodeRadius;
    }

    public double getPickRadius()
    {
	return m_pickRadius;
    }

    public double getPickEquivalenceRadius()
    {
	return m_pickEquivalenceRadius;
    }

    public double getPixelToMeterScale()
    {
	return m_pixelToMeterScale;
    }

    ////////////////////////////////////////////////////////////////////////
    // PRIVATE METHODS
    ////////////////////////////////////////////////////////////////////////

    private void refreshAll()
    {
	m_canvas.getCenterEyeInImagePlate(m_eye);
	m_imageToEye.set(new Vector3d(-m_eye.x, -m_eye.y, 0.0));
	m_eyeToImage.set(new Vector3d(m_eye.x, m_eye.y, 0.0));
	m_canvas.getVworldToImagePlate(m_vworldToImage);
	m_canvas.getImagePlateToVworld(m_imageToVworld);
    }

    private double computePixelToMeterScale(Screen3D screen)
    {
	double wm = screen.getPhysicalScreenWidth();
	double hm = screen.getPhysicalScreenHeight();

	java.awt.Dimension d = screen.getSize();
	int wp = d.width;
	int hp = d.height;

	double xScale = wm / wp;
	double yScale = hm / hp;

	if (Math.abs(xScale - yScale) > 1.0e-10)
	{
	    System.err.println(
		    "WARNING: computePixelToMeterScale(): "
		    + "xScale(" + xScale + ") != yScale(" + yScale + ").");
	}

	return xScale;
    }

    private void initializeBasicAppearances()
    {
	LineAttributes lineAttributes = new LineAttributes();
	lineAttributes.setLineAntialiasingEnable(ANTIALIASING);

	PointAttributes pointAttributes = new PointAttributes();
	pointAttributes.setPointSize(GENERAL_POINT_SIZE);
	pointAttributes.setPointAntialiasingEnable(ANTIALIASING);

	ColoringAttributes pointColoringAttributes =
	    new ColoringAttributes(0.0f, 0.0f, 1.0f,
				   ColoringAttributes.FASTEST);

	ColoringAttributes lineColoringAttributes =
	    new ColoringAttributes(0.7f, 0.7f, 0.7f,
	    //new ColoringAttributes(0.11765f, 0.58824f, 0.1f,
				   ColoringAttributes.FASTEST);

	// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

	m_pointAppearance = new Appearance();
	m_pointAppearance.setLineAttributes(lineAttributes);
	m_pointAppearance.setPointAttributes(pointAttributes);
	m_pointAppearance.setColoringAttributes(pointColoringAttributes);

	m_lineAppearance = new Appearance();
	m_lineAppearance.setLineAttributes(lineAttributes);
	m_lineAppearance.setPointAttributes(pointAttributes);
	m_lineAppearance.setColoringAttributes(lineColoringAttributes);

	// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

	m_transparencyAttributes =
	    //new TransparencyAttributes(TransparencyAttributes.BLENDED, 0.95f);
	    new TransparencyAttributes(TransparencyAttributes.BLENDED, 0.8f);
    }

    private void initializeGraphAppearances()
    {
	LineAttributes lineAttributes = new LineAttributes();
	lineAttributes.setLineAntialiasingEnable(ANTIALIASING);

	PointAttributes pointAttributes = new PointAttributes();
	pointAttributes.setPointSize(NODE_POINT_SIZE);
	pointAttributes.setPointAntialiasingEnable(ANTIALIASING);

	ColoringAttributes coloringAttributes =
	    new ColoringAttributes(1.0f, 1.0f, 0.0f,
				   ColoringAttributes.FASTEST);

	ColoringAttributes treeColoringAttributes = 
	    new ColoringAttributes(0.11765f, 0.58824f, 0.1f,
				   ColoringAttributes.FASTEST);

	ColoringAttributes nontreeColoringAttributes = 
	    new ColoringAttributes(0.7f, 0.7f, 0.7f,
				   ColoringAttributes.FASTEST);

	// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

	m_nodeAppearance = new Appearance();
	m_nodeAppearance.setLineAttributes(lineAttributes);
	m_nodeAppearance.setPointAttributes(pointAttributes);
	m_nodeAppearance.setColoringAttributes(coloringAttributes);

	// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

	m_treeLinkAppearance = new Appearance();
	m_treeLinkAppearance.setLineAttributes(lineAttributes);
	m_treeLinkAppearance.setPointAttributes(pointAttributes);
	m_treeLinkAppearance.setColoringAttributes(treeColoringAttributes);

	// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

	m_nontreeLinkAppearance = new Appearance();
	m_nontreeLinkAppearance.setLineAttributes(lineAttributes);
	m_nontreeLinkAppearance.setPointAttributes(pointAttributes);
	m_nontreeLinkAppearance
	    .setColoringAttributes(nontreeColoringAttributes);
    }

    private void initializeGradedNodeAppearances()
    {
	LineAttributes lineAttributes = new LineAttributes();
	lineAttributes.setLineAntialiasingEnable(ANTIALIASING);

	PointAttributes nearPointAttributes = new PointAttributes();
	nearPointAttributes.setPointSize(NODE_NEAR_POINT_SIZE);
	nearPointAttributes.setPointAntialiasingEnable(ANTIALIASING);

	PointAttributes middlePointAttributes = new PointAttributes();
	middlePointAttributes.setPointSize(NODE_MIDDLE_POINT_SIZE);
	middlePointAttributes.setPointAntialiasingEnable(ANTIALIASING);

	PointAttributes farPointAttributes = new PointAttributes();
	farPointAttributes.setPointSize(NODE_FAR_POINT_SIZE);
	farPointAttributes.setPointAntialiasingEnable(ANTIALIASING);

	ColoringAttributes coloringAttributes =
	    new ColoringAttributes(1.0f, 1.0f, 0.0f,
				   ColoringAttributes.FASTEST);

	// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

	m_nearNodeAppearance = new Appearance();
	m_nearNodeAppearance.setLineAttributes(lineAttributes);
	m_nearNodeAppearance.setPointAttributes(nearPointAttributes);
	m_nearNodeAppearance.setColoringAttributes(coloringAttributes);

	m_middleNodeAppearance = new Appearance();
	m_middleNodeAppearance.setLineAttributes(lineAttributes);
	m_middleNodeAppearance.setPointAttributes(middlePointAttributes);
	m_middleNodeAppearance.setColoringAttributes(coloringAttributes);

	m_farNodeAppearance = new Appearance();
	m_farNodeAppearance.setLineAttributes(lineAttributes);
	m_farNodeAppearance.setPointAttributes(farPointAttributes);
	m_farNodeAppearance.setColoringAttributes(coloringAttributes);
    }

    private void initializePickAppearances()
    {
	PointAttributes pointAttributes = new PointAttributes();
	pointAttributes.setPointSize(PICK_POINT_SIZE);
	pointAttributes.setPointAntialiasingEnable(ANTIALIASING);

	ColoringAttributes pointColoringAttributes =
	    new ColoringAttributes(0.9f, 0.1f, 0.1f,
				   ColoringAttributes.FASTEST);

	m_pickAppearance = new Appearance();
	m_pickAppearance.setPointAttributes(pointAttributes);
	m_pickAppearance.setColoringAttributes(pointColoringAttributes);
    }

    ////////////////////////////////////////////////////////////////////////
    // PRIVATE FIELDS
    ////////////////////////////////////////////////////////////////////////

    private static final boolean DEBUG_PRINT = false;
    private static final boolean ANTIALIASING = false;

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private H3Canvas3D m_canvas;
    private H3Axes m_axes = new H3Axes();
    private H3PickViewer m_pickViewer;
    private H3Circle m_nodeImage = new H3Circle();
    private boolean m_axesEnabled = true;

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private Transform3D m_objectTransform = new Transform3D();
    private Transform3D m_savedObjectTransform = new Transform3D();

    private Point3d m_eye = new Point3d();
    private Transform3D m_imageToEye = new Transform3D();
    private Transform3D m_eyeToImage = new Transform3D();
    private Transform3D m_vworldToImage = new Transform3D();
    private Transform3D m_imageToVworld = new Transform3D();

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private double m_pixelToMeterScale;

    private static final float GENERAL_POINT_SIZE = 4.0f;
    private static final float NODE_POINT_SIZE = 4.0f;
    private static final float NODE_NEAR_POINT_SIZE = 5.0f;
    private static final float NODE_MIDDLE_POINT_SIZE = 3.0f;
    private static final float NODE_FAR_POINT_SIZE = 1.0f;

    private static final int NODE_RADIUS_PIXELS = 10;
    private double m_nodeRadius;

    // PICK_EQUIVALENCE_RADIUS_PIXELS, which must be less than or equal
    // to PICK_RADIUS_PIXELS, is the radius within which nodes are
    // considered to overlap.  With this radius, we should pick the
    // overlapping node that is closest to the eye along the line of sight.
    private static final int PICK_RADIUS_PIXELS = 10;
    private static final int PICK_EQUIVALENCE_RADIUS_PIXELS = 2;
    private static final float PICK_POINT_SIZE = 10.0f;
    private double m_pickRadius;
    private double m_pickEquivalenceRadius;

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    // Some good combinations of front and back distances are
    // (1.0, 3.5), (1.25, 3.5), (1.0, 4.0), (1.25, 4.0), and (1.5, 4.0).
    private static final double DEPTH_CUEING_FRONT = 1.25;
    private static final double DEPTH_CUEING_BACK = 3.5;

    private boolean m_depthCueingEnabled = true;
    private LinearFog m_depthCueing;

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private static final double LABEL_SCALE = 0.03;
    private static final double LABEL_Z_SCALE = 0.5;
    private static final double LABEL_Z_OFFSET = 0.0; // meters
    private static final double LABEL_Z_OFFSET_SCALE = 0.0001; // meters
    private static final double LABEL_X_OFFSET = 10; // pixels
    private static final double LABEL_Y_OFFSET = 10; // pixels

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private TransparencyAttributes m_transparencyAttributes;

    private Appearance m_pointAppearance;
    private Appearance m_lineAppearance;

    private Appearance m_nodeAppearance;
    private Appearance m_treeLinkAppearance;
    private Appearance m_nontreeLinkAppearance;

    private Appearance m_nearNodeAppearance;
    private Appearance m_middleNodeAppearance;
    private Appearance m_farNodeAppearance;

    private Appearance m_pickAppearance;
}
