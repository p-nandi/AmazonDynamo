package edu.buffalo.cse.cse486586.simpledynamo;

import java.net.Socket;

/**
 * Created by phantom on 4/12/15.
 */
public class Message {

    public Message(String message,Socket socket){
        this.message = message;
        this.socket = socket;
    }

    public String message;

    public Socket socket;
}
