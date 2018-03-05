/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package be.bigboysystems.thirdi.cardboardtest;

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;

import com.google.vr.sdk.base.Eye;
import com.google.vr.sdk.base.GvrActivity;
import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.microedition.khronos.egl.EGLConfig;

/**
 * A Cardboard sample application.
 *
 * Disclaimer :
 *
 * This example is based on the orignial example provided by Matthew Wellings back in 2016.
 *
 * I only do an update and cut big parts of useless code in order to make it more simple. This quick-dirty
 * example just display a stereoscopic over/under video (put in the asset folder) with a gvrView.
 *
 * Student
 */
public class MainActivity extends GvrActivity implements GvrView.StereoRenderer {
  private static final String TAG = "MainActivity";

  private static final float Z_NEAR = 0.1f;
  private static final float Z_FAR = 100.0f;

  private static final float CAMERA_Z = 0.01f;

  private float[] camera;
  private float[] view;
  private float[] headView;

  private float[] headRotation;

  private VideoRenderer mVideoRenderer;
  private float[] videoScreenModelMatrix = new float[16];
  boolean renderSereo = true;

  /**
   * Converts a raw text file, saved as a resource, into an OpenGL ES shader.
   *
   * @param type The type of shader we will be creating.
   * @param resId The resource ID of the raw text file about to be turned into a shader.
   * @return The shader object handler.
   */
  public int loadGLShader(int type, int resId) {
    String code = readRawTextFile(resId);
    int shader = GLES20.glCreateShader(type);
    GLES20.glShaderSource(shader, code);
    GLES20.glCompileShader(shader);

    // Get the compilation status.
    final int[] compileStatus = new int[1];
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

    // If the compilation failed, delete the shader.
    if (compileStatus[0] == 0) {
      Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader));
      GLES20.glDeleteShader(shader);
      shader = 0;
    }

    if (shader == 0) {
      throw new RuntimeException("Error creating shader.");
    }

    return shader;
  }

  /**
   * Checks if we've had an error inside of OpenGL ES, and if so what that error is.
   *
   * @param label Label to report in case of error.
   */
  public static void checkGLError(String label) {
    int error;
    while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
      Log.e(TAG, label + ": glError " + error);
      throw new RuntimeException(label + ": glError " + error);
    }
  }

  /**
   * Sets the view to our CardboardView and initializes the transformation matrices we will use
   * to render our scene.
   */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.common_ui);
    GvrView cardboardView = (GvrView) findViewById(R.id.cardboard_view);
    cardboardView.setRenderer(this);
    this.setGvrView(cardboardView);

    camera = new float[16];
    view = new float[16];
    headRotation = new float[4];
    headView = new float[16];
  }

  @Override
  public void onPause() {
    super.onPause();
    Log.i(TAG, "onPause()");
    if (mVideoRenderer!=null)
      mVideoRenderer.pause();
  }

  @Override
  public void onResume() {
    super.onResume();
    Log.i(TAG, "onResume()");
    if (mVideoRenderer != null) {
      mVideoRenderer.start();
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    Log.i(TAG, "onStart()");
  }

  @Override
  public void onStop() {
    super.onStop();
    Log.i(TAG, "onStop()");
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Log.i(TAG, "onDestroy()");
    if (mVideoRenderer != null) {
      mVideoRenderer.cleanup();
    }
  }

  @Override
  public void onRendererShutdown() {
    Log.i(TAG, "onRendererShutdown");
  }

  @Override
  public void onNewFrame(HeadTransform headTransform) {

    // Build the camera matrix and apply it to the ModelView.
    // This line set the camera looking at our virtual screen
    Matrix.setLookAtM(camera, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, -3f, 0.0f, 1.0f, 0.0f);


    checkGLError("onReadyToDraw");
  }

  @Override
  public void onDrawEye(Eye eye) {

    GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

    checkGLError("colorParam");

    // Apply the eye transformation to the camera.
    // By commenting this line, you do not apply head transformation to the camera so it is static
    //Matrix.multiplyMM(view, 0, eye.getEyeView(), 0, camera, 0);

    // Build the ModelView and ModelViewProjection matrices
    float[] perspective = eye.getPerspective(Z_NEAR, Z_FAR);

    float[] videoMVP =  new float[16];
    Matrix.multiplyMM(videoMVP, 0, camera, 0, videoScreenModelMatrix, 0);
    Matrix.multiplyMM(videoMVP, 0, perspective, 0, videoMVP, 0);
    mVideoRenderer.setMVPMatrix(videoMVP);
    mVideoRenderer.render(eye.getType());
  }

  @Override
  public void onFinishFrame(Viewport viewport) {

  }

  @Override
  public void onSurfaceChanged(int width, int height) {
    Log.i(TAG, "onSurfaceChanged");
  }

  /**
   * Creates the buffers we use to store information about the 3D world.
   *
   * <p>OpenGL doesn't use Java arrays, but rather needs data in a format it can understand.
   * Hence we use ByteBuffers.
   *
   * @param config The EGL configuration used when creating the surface.
   */
  @Override
  public void onSurfaceCreated(EGLConfig config) {
    Log.i(TAG, "onSurfaceCreated");
    GLES20.glClearColor(0f, 0f, 0f, 0.5f); // Dark background so text shows up well.

    //Setup video renderer:
    mVideoRenderer = new VideoRenderer(this);
    mVideoRenderer.setup();
    mVideoRenderer.start();

    //Set the size and placement of the virtual screen.
    Matrix.setIdentityM(videoScreenModelMatrix, 0);
    float screenSize=3f; //Virtual screen height in meters.
    float aspectRatio=16f/9f; //Image will be stretched to this ratio.
    Matrix.scaleM(videoScreenModelMatrix, 0, screenSize, screenSize/aspectRatio, 1);
    Matrix.translateM(videoScreenModelMatrix, 0, 0.0f, 0.0f, -4f);

    checkGLError("onSurfaceCreated");
  }

  /**
   * Converts a raw text file into a string.
   *
   * @param resId The resource ID of the raw text file about to be turned into a shader.
   * @return The context of the text file, or null in case of error.
   */
   private String readRawTextFile(int resId) {
    InputStream inputStream = getResources().openRawResource(resId);
    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append("\n");
      }
      reader.close();
      return sb.toString();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * Called when the Cardboard trigger is pulled.
   */
  @Override
  public void onCardboardTrigger() {
  }
}