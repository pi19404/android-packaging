/*==============================================================================
            Copyright (c) 2012 QUALCOMM Austria Research Center GmbH.
            All Rights Reserved.
            Qualcomm Confidential and Proprietary
            
@file 
    ImageTargetsRenderer.java

@brief
    Sample for ImageTargets

==============================================================================*/


package com.packagingmidsweden.packaging;

import java.io.IOException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.util.Log;

import com.packagingmidsweden.packaging.R;
import com.qualcomm.QCAR.QCAR;
import com.threed.jpct.Camera;
import com.threed.jpct.FrameBuffer;
import com.threed.jpct.Light;
import com.threed.jpct.Loader;
import com.threed.jpct.Matrix;
import com.threed.jpct.Object3D;
import com.threed.jpct.RGBColor;
import com.threed.jpct.SimpleVector;
import com.threed.jpct.Texture;
import com.threed.jpct.TextureManager;
import com.threed.jpct.World;
import com.threed.jpct.util.BitmapHelper;
import com.threed.jpct.util.MemoryHelper;


/** The renderer class for the ImageTargets sample. */
public class ImageTargetsRenderer implements GLSurfaceView.Renderer 
{
	
	public ImageTargetsRenderer (Context context) {
		this.mContext = context ;
	}
	
	 // Renderer status flags 
    private boolean IDLE        	  = false;  // No trackable found
    private boolean TRACKABLE_FOUND   = false;  // Trackable found but the 3D model is not ready to render
    private boolean LOADING_MODEL     = false;  // MD2 model is being loaded
    private boolean MODEL_READY 	  = false;  // MD2 is ready to render
    protected boolean MODEL_ANIMATION	  = false;  // Whether the model is animation or not
  
	
    public boolean mIsActive = false;
    
    // Application context
    private Context mContext;
    
    /** jPCT-AE */
	private FrameBuffer mFrameBuffer = null;
	private World mWorld = null;
	private RGBColor mBackground = new RGBColor(0, 0, 0, 0);
	private Object3D mArrow = null;
	private int fps = 0;
	private Light mSun = null;
	private float[] mFieldofView;
	private Matrix mMatrix = new Matrix(); 
	private SimpleVector mSunVector;
	private float mAnimation = 0;
	
	private Camera mCamera = null;
	
	private String mTrackableName = "";
	private Object3D mModel = null;
	private ModelLoader mModelLoader;
	
	// The actual camera resolution which QCAR uses (used to set the framebuffer size) 
	private int mResolution[];
	/** jPCT-AE */
    
    /** Native function for initializing the renderer. */
    public native void initRendering();
    
    
    /** Native function to update the renderer. 
     * @return */
    public native int[] updateRendering(int width, int height);
    
	// ModelView and Projection Matrices
	private float mModelviewArray[] = new float[16];

    
    /** Called when the surface is created or recreated. */
    public void onSurfaceCreated(GL10 gl, EGLConfig config)
    {
        DebugLog.LOGD("GLRenderer::onSurfaceCreated");

        // Call native function to initialize rendering:
        initRendering();
        
        // Call QCAR function to (re)initialize rendering after first use
        // or after OpenGL ES context was lost (e.g. after onPause/onResume):
        QCAR.onSurfaceCreated();
        
    }
    
    
    /** Called when the surface changed size. */
    public void onSurfaceChanged(GL10 gl, int width, int height)
    {
        DebugLog.LOGD("GLRenderer::onSurfaceChanged");
        
        // Call native function to update rendering when render surface parameters have changed:
        mResolution = updateRendering(width, height);

        // Call QCAR function to handle render surface size changes:
        QCAR.onSurfaceChanged(width, height);
        
        // Retrieve the Field of View from QCAR
        mFieldofView = getFOV();
        Log.d("FOV Horizontal", Float.toString(mFieldofView[0]));
        Log.d("FOV Vertical", Float.toString(mFieldofView[1]));
        
        
        /** jPCT-AE */
        if (mFrameBuffer != null) {
        	mFrameBuffer.dispose();
		}
        
        // Creating the framebuffer with the actual camera resolution
        mFrameBuffer = new FrameBuffer(gl, mResolution[0], mResolution[1]);
//        mFrameBuffer = new FrameBuffer(width, height);
        
        // Our 3D World
		mWorld = new World();
		mWorld.setAmbientLight(100, 100, 100);
		mWorld.setClippingPlanes(2.0f, 2000.0f);
		
		// Our Light
		mSun = new Light(mWorld);
		mSun.enable();
		mSun.setIntensity(255, 255, 255);
		
		// Create a solid color texture
		Texture mTexture = null;
		Texture mArrowTexture = null;
		mArrowTexture = new Texture(10, 10, new RGBColor(150, 150, 150));
		
		try {
			
			// No need to re-scale the texture 
			mTexture = new Texture(BitmapHelper.loadImage(mContext.getAssets().open("tetraTexture1.jpg")));
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// Memory related
		mTexture.enable4bpp(true);
//		mTexture.setMipmap(true);
		
		// Do not load texture if it's already loaded
		if(TextureManager.getInstance().containsTexture("texture")) {
			
			// Model Texture
			TextureManager.getInstance().unloadTexture(mFrameBuffer, 
					TextureManager.getInstance().getTexture("texture"));
			TextureManager.getInstance().replaceTexture("texture", mTexture);
			
			// Arrow Texture
			TextureManager.getInstance().unloadTexture(mFrameBuffer, 
					TextureManager.getInstance().getTexture("texture"));
			TextureManager.getInstance().replaceTexture("texture", mTexture);
			
		} else {
			TextureManager.getInstance().addTexture("texture", mTexture);
			TextureManager.getInstance().addTexture("arrow", mArrowTexture);
		}
		
//		// Load MD2 model in background
//		mModelLoader =  new ModelLoader();
//		mModelLoader.execute();
		
		
		// Loading arrow 3D model
		try {
			mArrow =  Loader.loadMD2(mContext.getAssets().open("Arrow1.md2"),1f);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// Model settings (arrow)
		mArrow.calcTextureWrap();
		mArrow.setTexture("arrow");
		mArrow.setLighting(Object3D.LIGHTING_ALL_ENABLED);
		mArrow.setTransparency(10);
		mArrow.strip();
		mArrow.build();

		mWorld.addObject(mArrow);

		mCamera = mWorld.getCamera();
		mCamera.setFOV(mFieldofView[0]);
//		mCamera.setYFOV(mFieldofView[1]);
		
		// Light position
		mSunVector = new SimpleVector();
		mSunVector.set(mArrow.getTransformedCenter());
		mSunVector.y -= 150;
		mSunVector.z -= 100;
		mSunVector.x += 100;

		mSun.setPosition(mSunVector);
		MemoryHelper.compact();
		/** jPCT-AE */
		

    }    
    
    
    /** The native render function. */    
    public native void renderFrame();
    
    
    /** Called to draw the current frame. */
    public void onDrawFrame(GL10 gl)
    {
        if (!mIsActive)
            return;
        
        GL11 gl11 = (GL11) gl;
        
        
        mFrameBuffer.clear(mBackground);
		
        // Call our native function to render video background
        renderFrame();
            
        // Important. Overrides the default QCAR initialization settings
        gl11.glEnable(GL11.GL_DEPTH_TEST);
        gl11.glEnable(GL11.GL_CULL_FACE);
        gl11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        gl11.glEnable(GL11.GL_LIGHTING);
        gl11.glEnable(GL11.GL_BLEND);
 
    	
//    	gl11.glEnable(GL11.GL_TEXTURE_2D);
        
        // If there is a trackable in current frame
        if (TRACKABLE_FOUND) {
        	
        	mMatrix.setDump(mModelviewArray);
        	mCamera.setBack(mMatrix);
        	
        	// If model is not ready to render
        	if (!MODEL_READY) {
        		
        		mArrow.rotateZ(-0.15f);
        		
        	} else if (MODEL_ANIMATION){
        		
//        		// Start the animation
//        		mModel.animate(mAnimation);
//    		
//        		// Animation speed
//        		mAnimation += 0.05;
//    		
//        		// Replay the animation
//        		if (mAnimation >= 1){
//        			mAnimation = 0;
//        		} 
//        		
        		mModel.rotateZ(0.15f);
        		
            }
            
            mWorld.renderScene(mFrameBuffer);
    		mWorld.draw(mFrameBuffer);
    		mFrameBuffer.display();
        	
    		// Reset the flag for next frame
    		TRACKABLE_FOUND = false;
        }
        
		
//		gl11.glDisable(GL11.GL_TEXTURE_2D);	

    }

	/** 
	 * Called by native code to store 
	 * ModelView Matrix when trackable 
	 * is detected 
	 */
    private void handleTrackable(String name, float modelviewArray[])
    {
//         mModelviewArray = modelviewArray;
         System.arraycopy(modelviewArray, 0, mModelviewArray, 0, mModelviewArray.length);
         
         // If there is no change in Trackable's name 
         if (!(mTrackableName.equalsIgnoreCase(name))) {
        	 
        	 // Store new Trackable's name
        	 mTrackableName = name;
        	 
        	 // Start the AsyncTaks to load MD2 in background
        	 mModelLoader =  new ModelLoader();
        	 mModelLoader.execute();
        	 
         }
  
         // Trackable Flag
         TRACKABLE_FOUND = true;
        
    }
    
    
    /** Called by native code to store target size */
    private void targetDistance(float distanceArray[])
    {
//    	float distance  = (float) Math.sqrt(distanceArray[0] * distanceArray[0] +
//    			distanceArray[1] * distanceArray[1] +
//    			distanceArray[2] * distanceArray[2]); 
//    	Log.d("Target Distance", Float.toString(distance));
    }
    
    /** Native method for getting the projection matrix */
    private native float[] getFOV();
    
    /** Cleans the renderer onDistroy */
    protected void cleanRenderer() {
    	
    	// Cancel AsyncTask onDestroy
    	if(mModelLoader!= null && !mModelLoader.isCancelled())
    		mModelLoader.cancel(true);
    	
    	TextureManager.getInstance().removeTexture("texture");
    	TextureManager.getInstance().removeTexture("arrow");
    	mArrow = null;
        mModel = null;
        mFrameBuffer.freeMemory();
        mFrameBuffer.dispose();
        mFrameBuffer = null;
        mWorld.dispose();
        mWorld = null;	
        Loader.clearCache();  
    }
    
    
    
    /** Asynchronous task to load MD2 models in background */
    private class ModelLoader extends AsyncTask<Void, Void, Void> {

    	
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
		}
		
		@Override
		protected Void doInBackground(Void... params) {
			
			// Loading an animated MD2 model from asset folder
			try {
//				mModel =  Loader.loadMD2(mContext.getAssets().open("Tetra(stand).md2"),2.8f);
				mModel =  Loader.loadMD2(mContext.getAssets().open("Flaska21.md2"),30.8f);
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			return null;
		}
		
		@Override
		protected void onProgressUpdate(Void... values) {
			super.onProgressUpdate(values);
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			
			// Model settings
//			mModel.setTexture("texture");
			mModel.setTexture("arrow");
		 	mModel.setLighting(Object3D.LIGHTING_ALL_ENABLED);
			mModel.strip();
			mModel.build();

			mWorld.addObject(mModel);

//			mCamera = mWorld.getCamera();
			
			// Light's position
			mSunVector = new SimpleVector();
			mSunVector.set(mModel.getTransformedCenter());
			mSunVector.y -= 150;
			mSunVector.z -= 100;
			mSunVector.x += 100;
			mSun.setPosition(mSunVector);
			
			Loader.clearCache();
			MemoryHelper.compact();
			
			
			// Loading has been done
			MODEL_READY = true;
			
			Log.d("Background Task", "3D model has been Loaded");
			
		}
    	
    }
    

  
}
