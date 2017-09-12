package com.frc8.team8vision.networking;

import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Base class for vision servers. Implements {@link AbstractVisionThread}.
 *
 * @author Quintin Dwight
 */
public abstract class AbstractVisionClient extends AbstractVisionThread {

    public enum ServerState {
        PRE_INIT, ATTEMPTING_CONNECTION, OPEN
    }

    protected boolean m_testing = false;
    protected int m_port = 0;
    protected ServerSocket m_server;
    protected Socket m_client;
    protected ServerState m_serverState = ServerState.PRE_INIT;

    protected AbstractVisionClient(final String k_threadName) {
        super(k_threadName);
    }

    @Override
    @Deprecated
    public void start(final long k_updateRate) {
        super.start(k_updateRate);
    }

    /**
     * Starts the server thread
     *
     * @param k_updateRate Update rate of the thread
     * @param k_testing Whether or not we are testing
     * @param k_port The port to connect the server to
     */
    public void start(final int k_updateRate, final boolean k_testing, final int k_port)
    {
        super.start(k_updateRate);

        m_testing = k_testing;
        m_port = k_port;
    }

    @Override
    protected void init() {

        if (m_serverState != ServerState.PRE_INIT) {
            Log.e(k_tag, "Thread has already been initialized. Aborting...");
            return;
        }

        // Try to create the server
        try {
            m_server = new ServerSocket(m_port);
            m_server.setReuseAddress(true);
        } catch (IOException e) {
            e.printStackTrace();
        }

        setServerState(ServerState.ATTEMPTING_CONNECTION);
    }

    /**
     * Sets the state of the server
     *
     * @param state State of the server
     */
    protected void setServerState(ServerState state) {
        m_serverState = state;
    }

    /**
     * Pauses the thread until a connection is established
     *
     * @return The state after execution
     */
    private ServerState acceptConnection(){

        try {
            // Pause thread until we accept from the client
            Log.e(k_tag, "Trying to connect to client...");
            m_client = m_server.accept();
            Log.e(k_tag, "Connected to client: " + m_client.getPort());
            return ServerState.OPEN;
        } catch (IOException e) {
            e.printStackTrace();
            return ServerState.ATTEMPTING_CONNECTION;
        }
    }

    @Override
    protected void update()
    {
        switch (m_serverState){

            case PRE_INIT:
                Log.e(k_tag, "Thread is not initialized while in update.");
                break;

            case ATTEMPTING_CONNECTION:
                setServerState(acceptConnection());
                break;
        }
    }
}