package com.frc8.team8vision;

import android.app.Activity;
import android.util.Log;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.net.Socket;

/**
 * Separate Thread to send image data to roboRIO
 * Strategies:
 *  - Write mat data to JSON, then read JSON
 *
 * <h1><b>Fields</b></h1>
 * 	<ul>
 * 		<li>Instance and State variables:
 * 			<ul>
 * 				<li>{@link JSONStreamerThread#s_instance} (Singleton): Private static instance of this class</li>
 * 				<li>{@link JSONStreamerThread#m_streamerThreadState} (private): Current state of connection</li>
 * 			    <li>{@link JSONStreamerThread#m_lastThreadState} (private): Last connection state</li>
 * 			    <li>{@link JSONStreamerThread#m_socketConnectionState} (private): Current writing state</li>
 * 			    <li>{@link JSONStreamerThread#m_activity} (private): Activity hosting the thread</li>
 * 				<li><b>See:</b>{@link StreamerThreadState}</li>
 * 			</ul>
 * 		</li>
 * 		<li>Utility variables:
 * 			<ul>
 * 				<li>{@link JSONStreamerThread#m_secondsAlive}: Private count of seconds the program has run for</li>
 * 				<li>{@link JSONStreamerThread#m_stateAliveTime}: Private count of seconds the state has run for</li>
 * 				<li>{@link JSONStreamerThread#m_writerInitialized}: Private boolean representing whether the writer for
 * 			                                                        the current writing state has been initialized	</li>
 * 				<li>{@link JSONStreamerThread#m_running}: Private boolean representing whether the thread is running</li>
 * 			</ul>
 * 		</li>
 * 	</ul>
 *
 * <h1><b>Accessors and Mutators</b></h1>
 * 	<ul>
 * 		<li>{@link JSONStreamerThread#getInstance()}</li>
 * 		<li>{@link JSONStreamerThread#SetState(StreamerThreadState)}</li>
 * 		<li>{@link JSONStreamerThread#SetConnectionState(SocketConnectionState)}</li>
 * 	</ul>
 *
 * <h1><b>External Access Functions</b>
 * 	<br><BLOCKQUOTE>For using as a wrapper for RIOdroid</BLOCKQUOTE></h1>
 * 	<ul>
 * 		<li>{@link JSONStreamerThread#start(Activity)}</li>
 * 		<li>{@link JSONStreamerThread#pause()}</li>
 * 		<li>{@link JSONStreamerThread#resume()}</li>
 * 		<li>{@link JSONStreamerThread#destroy()}</li>
 * 	</ul>
 *
 * 	<h1><b>Internal Functions</b>
 * 	 <br><BLOCKQUOTE>Paired with external access functions. These compute the actual function for the external access</BLOCKQUOTE></h1>
 * 	 <ul>
 * 	     <li>{@link JSONStreamerThread#InitializeWriter()}</li>
 * 	     <li>{@link JSONStreamerThread#WriteImageData()}</li>
 * 	 </ul>
 *
 * Created by Alvin on 2/16/2017.
 * @see StreamerThreadState
 * @see SocketConnectionState
 * 	 </ul>
 *
 * Created by Alvin on 2/16/2017.
 * @author Alvin
 */

public class JSONStreamerThread implements Runnable {

	/**
	 *  State of the thread controlling the writer
	 *
	 *  <ul>
	 *      <li>{@link StreamerThreadState#PREINIT}</li>
	 *      <li>{@link StreamerThreadState#IDLE}</li>
	 *      <li>{@link StreamerThreadState#WRITING}</li>
	 *      <li>{@link StreamerThreadState#INITIALIZE_WRITER}</li>
	 *      <li>{@link StreamerThreadState#PAUSED}</li>
	 *  </ul>
	 */
	public enum StreamerThreadState {
		PREINIT, IDLE, WRITING, INITIALIZE_WRITER, PAUSED
	}

	/**
	 *  State of the writer
	 *
	 *  <ul>
	 *      <li>{@link SocketConnectionState#ALIVE}</li>
	 *      <li>{@link SocketConnectionState#CLOSED}</li>
	 *  </ul>
	 */
	public enum SocketConnectionState {
		ALIVE, CLOSED
	}

	// Instance and state variables
	public static JSONStreamerThread s_instance;
	private StreamerThreadState m_streamerThreadState = StreamerThreadState.PREINIT;
	private StreamerThreadState m_lastThreadState;
	private SocketConnectionState m_socketConnectionState = SocketConnectionState.CLOSED;
	private Activity m_activity;
	private Socket m_socket;

	// Utility variables
	private double m_secondsAlive = 0;
	private double m_stateAliveTime = 0;
	private double m_lastFrameTime = 0;
	private boolean m_writerInitialized = false;
	private boolean m_running = false;

	/**
	 * Creates a JSONStreamerThread instance
	 * Cannot be called outside as a Singleton
	 */
	private JSONStreamerThread(){}

	/**
	 * @return The instance of the WTD
	 */
	public static JSONStreamerThread getInstance(){
		if(s_instance == null){
			s_instance = new JSONStreamerThread();
		}
		return s_instance;
	}

	/**
	 * Sets the state of the writer
	 * @param state State to switch to
	 */
	private void SetState(StreamerThreadState state){
		if(!m_streamerThreadState.equals(state)){
			try {
				Thread.sleep(Constants.kChangeStateWaitMS);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			m_streamerThreadState = state;
		}
	}

	/**
	 * Sets the state of the writing
	 * @param state State to switch to
	 */
	private void SetConnectionState(SocketConnectionState state){
		if(m_socketConnectionState.equals(state)){
			Log.w("SocketState Warning:", "no change to write state");
		}else{
			m_socketConnectionState = state;
		}
	}

	/**
	 * (Debug) Logs the Thread state
	 */
	private void logThreadState(){
		Log.d("JSONStreamer State", "ThreadState: "+ m_streamerThreadState);
	}

	/**
	 * (DEBUG) Logs the Write state
	 */
	private void logWriteState(){
		Log.d("SocketState state", "SocketConnectionState: "+ m_socketConnectionState);
	}

	/**
	 * Starts the JSONStreamerThread Thread
	 * <br> start() will only set values, check for errors, and prepare for thread creation
	 * <br> actual creation of thread completed in resume(), which is called during app startup
	 * @param activity Parent activity of the Thread
	 */
	public void start(Activity activity){
		if(s_instance == null) { // This should never happen
			Log.e("JSONStreamer Error", "No initialized instance, this should never happen");
			return;
		}

		if(!m_streamerThreadState.equals(StreamerThreadState.PREINIT)){
			Log.e("JSONStreamer Error", "Thread has already been initialized");
			this.logThreadState();
		}

		if(m_running){
			Log.e("JSONStreamer Error", "Thread is already running");
			return;
		}
		m_activity = activity;
		m_running = true;
	}

	/**
	 * Pauses the Thread
	 * <br>Called when the program pauses/releases the Camera
	 */
	public void pause(){
		if(!m_running){
			Log.e("JSONStreamer Error", "Thread is not running");
		}

		m_lastThreadState = m_streamerThreadState;
		this.SetState(StreamerThreadState.PAUSED);
	}

	/**
	 * Either resumes or initializes the Thread<br>
	 * <br>Two Possibilities
	 * <ul>
	 *     <li>
	 *         Thread state is paused
	 *         <br>Normally resume the thread from paused state
	 *         <br>({@link StreamerThreadState#PAUSED})
	 *     </li>
	 *
	 *     <li>
	 *         Thread state is pre-initialization
	 *         <br>Complete second part of thread initialization:
	 *         <br><BREAKQUOTE>Set the thread state and start running the actual thread</BREAKQUOTE>
	 *         <br>({@link StreamerThreadState#PAUSED})
	 *     </li>
	 * </ul>
	 */
	public void resume(){
		if(m_streamerThreadState.equals(StreamerThreadState.PAUSED)){   // If paused, continue from where we left off
			this.SetState(m_lastThreadState);
		}else if(m_streamerThreadState.equals(StreamerThreadState.PREINIT)){  // If thread is initializing, finish initialization
			this.SetState(StreamerThreadState.INITIALIZE_WRITER);

			Log.i("JSONStreamer Thread", "Starting Thread: JSONStreamerThread");
			(new Thread(this, "JSONStreamerThread")).start();
		}else{  // This should never happen
			Log.e("JSONStreamer Error", "Trying to resume a non-paused Thread");
			this.logThreadState();
		}
	}

	/**
	 * Destroys the Thread<br>
	 * <br><b>Note:</b> if the app is not formally closed,
	 *           the WDT object will not be destroyed,
	 *           but thread will still stop and need
	 *           to be started again for use upon
	 *           re-opening the app
	 */
	public void destroy(){
		m_running = false;
		this.closeSocket();
		this.SetState(StreamerThreadState.PREINIT);
	}

	private void closeSocket(){
		Log.w("JSONStreamer Warning", "Time since last sent frame has exceeded the timeout of " +
				Constants.kVisionIdleTimeS + ", closing the socket");
		try {
			m_socket.close();
		} catch (IOException e) {
			Log.e("JSONStreamer Error", "Error while closing the socket: "+e.getStackTrace().toString());
		}
		this.SetConnectionState(SocketConnectionState.CLOSED);
	}

	/**
	 * Initializes the selected method of writing data
	 * @return The state after execution
	 */
	private StreamerThreadState InitializeWriter(){
		// FOR METHODS OTHER THAN JSON WRITING, EDIT
		switch (m_socketConnectionState){
			case ALIVE:
				Log.e("SocketState Error", "Trying to initialize an live writer...");
				break;
			case CLOSED:
				try {
					Log.i("JSONStreamer Socket", "Trying to connect to server");
					m_socket = new Socket(Constants.kRIOHostName, Constants.kVisionPortNumber);
					Log.i("JSONStreamer Socket", "Connected to socket on port "+m_socket.getPort());
					this.SetConnectionState(SocketConnectionState.ALIVE);
				} catch (IOException e) {
					Log.e("JSONStreamer Error", "Socket could not connect, retrying: "+e.getStackTrace().toString());
					return StreamerThreadState.INITIALIZE_WRITER;
				}
				break;
		}
		return StreamerThreadState.WRITING;
	}

	/**
	 * Writes matrix data to JSON file
	 * @return The state after execution
	 */
	private StreamerThreadState WriteImageData() {
		// Initialize data
		byte[] imgdata = getImageJPEGBytes();

		// Check if data is empty
		if(imgdata == null || imgdata.length == 0){

			// If last frame time exceeds a limit then close the socket
			if(m_lastFrameTime >= Constants.kVisionIdleTimeS){
				this.closeSocket();
			}
		} else {

			// If the socket is closed, reopen
			if(m_socketConnectionState.equals(SocketConnectionState.CLOSED)){
				InitializeWriter();
			}

			try {
				// Initialize data streams
				OutputStream out = m_socket.getOutputStream();
				DataOutputStream dos = new DataOutputStream(out);

				dos.writeInt(imgdata.length);
				dos.write(imgdata, 0, imgdata.length);
			} catch (IOException e) {
				Log.e("JSONStreamer Error", "Cannot get output stream of socket");
				// This shouldn't happen so idek
			}
		}

		return m_streamerThreadState;
	}

	private byte[] getImageJPEGBytes(){
		// Initialize images
		Mat imageRGB = MainActivity.getImage();
		if (imageRGB == null || imageRGB.empty()) {
			return null;
		}

		// Resize image for smaller streaming size
//		Imgproc.resize(imageRGB, imageRGB, new Size(320, 180));

		// Convert Mat to JPEG byte array
		MatOfByte bytemat = new MatOfByte();
		Imgcodecs.imencode(".jpg", imageRGB, bytemat);

		return bytemat.toArray();
	}

	/**
	 * Updates the thread at {@link Constants#kVisionUpdateRateMS} ms
	 */
	@Override
	public void run() {
		while(m_running){
			StreamerThreadState initState = m_streamerThreadState;
			switch (m_streamerThreadState){

				case PREINIT:   // This should never happen
					Log.e("JSONStreamer Error", "Thread running, but has not been initialized");
					break;

				case INITIALIZE_WRITER:
					this.SetState(this.InitializeWriter());
					break;

				case WRITING:
					this.SetState(this.WriteImageData());
					break;

				case PAUSED:
					break;

			}

			// Reset state start time if state changed
			if(!initState.equals(m_streamerThreadState)){
				m_stateAliveTime = m_secondsAlive;
			}

			// Handle thread sleeping, sleep for set time delay
			try{
				Thread.sleep(Constants.kVisionUpdateRateMS);
				m_secondsAlive += Constants.kVisionUpdateRateMS /1000.0;
			}catch (InterruptedException e){
				e.printStackTrace();
			}
		}
	}
}

