package edu.buffalo.cse.cse486586.simpledynamo;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * Created by phantom on 4/11/15.
 */
public class CommonUtil {

    static final String TAG = CommonUtil.class.getSimpleName();


/*    public static String sendMessage(String message, String port) throws Exception{
        Socket socket = null;
        String rcvdMessage=null;
        try {
            socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                    Integer.parseInt(port));
            socket.setSoTimeout(1500);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.writeObject(message);
            out.flush();
            Log.v(TAG, "sendMessage:: " + message + " sent to " + port);
            //Receive ACK of insert
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            rcvdMessage = (String)in.readObject();
            Log.v(TAG, "Received message "+rcvdMessage);
            //out.close();
            //in.close();
        } catch (UnknownHostException e) {
            Log.e(TAG, "ClientTask UnknownHostException");
            throw e;
        } catch (SocketTimeoutException e) {
            Log.e(TAG, "SocketTimeoutException has occured");
            throw e;
        } catch (IOException e) {
            Log.e(TAG, "ClientTask socket IOException");
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "Some other exception occured in unicast");
            e.printStackTrace();
            throw e;
        }finally {

        }
        return rcvdMessage;
    }*/



    public static void unicastMessage(String message, String port) {
        Socket socket = null;
        try {
            socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                    Integer.parseInt(port));
            PrintWriter out = new PrintWriter(socket.getOutputStream());
            out.write(message);
            out.flush();
            out.close();
            socket.close();
            Log.v(TAG,"unicastMessage:: "+message+"Message sent to "+port);
        } catch (UnknownHostException e) {
            Log.e(TAG, "ClientTask UnknownHostException");
        } catch (IOException e) {
            Log.e(TAG, "ClientTask socket IOException");
        } catch (Exception e) {
            Log.e(TAG, "Some other exception occured in unicast");
            e.printStackTrace();;
        }finally {

        }
    }

    public static Boolean isLessThanEqual(String hashVal1,String hashVal2){
        int compareVal = hashVal1.compareTo(hashVal2);
        if(compareVal<=0){
            return true;
        }
        else{
            return false;
        }
    }

}
