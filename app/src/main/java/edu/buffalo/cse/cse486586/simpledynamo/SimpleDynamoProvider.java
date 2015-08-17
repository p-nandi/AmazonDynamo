package edu.buffalo.cse.cse486586.simpledynamo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SimpleDynamoProvider extends ContentProvider {

    static final int SERVER_PORT = 10000;

    static final String TAG = SimpleDynamoProvider.class.getSimpleName();

    public static String localPort;

    Set<String> ports;

    List<Node> dynamoNodes;

    static final int NUM_NODES = 5;

    static final int NUM_REPLICAS = 3;

    Uri providerUri;

    Map<String,String> newInserts;

    Boolean recoveryInProgress;


    @Override
    public boolean onCreate() {
        //Log.v(TAG,"onCreate:: Oncreate started");

        providerUri = Uri.parse("content://" + "edu.buffalo.cse.cse486586.simpledynamo.provider"
                + "/edu.buffalo.cse.cse486586.simpledynamo.SimpleDynamoProvider");
        newInserts = new HashMap<>();
        recoveryInProgress = false;
        /**
         * Calculate the local port number
         */
        TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        localPort = String.valueOf((Integer.parseInt(portStr) * 2));

        ports = new HashSet<String>();
        dynamoNodes = new ArrayList<Node>();
        ports.add("11108");
        ports.add("11112");
        ports.add("11116");
        ports.add("11120");
        ports.add("11124");
        for(String port:ports){
            Node node = new Node(port,convertPortNumToHashVal(port));
            dynamoNodes.add(node);
        }
        Collections.sort(dynamoNodes,new NodeHashValComparator());

         /*
         * Create a server socket as well as a thread (AsyncTask) that listens on the server
         * port.
         */

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
        }

        /**
         * Start recovery
         */
        File filesDir=getContext().getFilesDir();
        if(filesDir.list()!=null && filesDir.list().length>0){
            recoveryInProgress = true;
            //Log.v(TAG,"Started Recovery Task");
            new RecoverTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
        }

        return false;
    }

    public static String sendMessage(String message, String port) throws Exception{
        Socket socket = null;
        String rcvdMessage=null;
        try {
            socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                    Integer.parseInt(port));
            socket.setSoTimeout(1500);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.writeObject(message);
            out.flush();
            //Log.v(TAG, "sendMessage:: " + message + " sent to " + port);
            //Receive ACK of insert
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            rcvdMessage = (String)in.readObject();
            //Log.v(TAG, "Received message "+rcvdMessage);
            out.close();
            in.close();
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
            e.printStackTrace();;
        }finally {

        }
        return rcvdMessage;
    }




    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        //Log.v(TAG,"delete method called");
        String key = selection;
        if(CommonConstants.QUERY_LOCAL.equals(key)){
            deleteLocalFiles();
        }else if(CommonConstants.QUERY_GLOBAL.equals(key)){
            deleteLocalFiles();
            StringBuilder sb = new StringBuilder();
            sb.append(CommonConstants.MSG_TYPE_DELETE).append(CommonConstants.HASH_SEP)
                    .append(localPort).append(CommonConstants.HASH_SEP)
                    .append(CommonConstants.QUERY_GLOBAL);
            String message=sb.toString();
            for(Node node : dynamoNodes){
                ////Log.v(TAG,"delete:: Forwarding delete global "+message +" to "+ node.getPortNum());
                if(!node.getPortNum().equals(localPort)){
                    try {
                        sendMessage(message, node.getPortNum());
                    }catch(Exception e){
                        //Log.v(TAG, "Error occured while deleting from port "+node.getPortNum());
                    }
                }
            }

        }else{
            int index = findSuccesssorIndex(key);
            int snd_cnt=0;
            int num_nodes = dynamoNodes.size();
            while(snd_cnt!=NUM_REPLICAS){
                Node node = dynamoNodes.get(index);
                ////Log.v(TAG, "Deleting specific key "+ key +" from "+node.getPortNum());
                if(node.getPortNum().equals(localPort)){
                    deleteSingleLocalFile(key);
                }else {
                    StringBuilder sb = new StringBuilder();
                    sb.append(CommonConstants.MSG_TYPE_DELETE).append(CommonConstants.HASH_SEP)
                            .append(localPort).append(CommonConstants.HASH_SEP)
                            .append(key);
                    String message=sb.toString();
                    ////Log.v(TAG,"delete:: Forwarding delete specific key "+message+" to "+node.getPortNum());
                    try {
                        sendMessage(message, node.getPortNum());
                    }catch(Exception e){
                        //Log.v(TAG, "Error occured while deleting from port "+node.getPortNum());
                    }
                }
                index = (index+1) % num_nodes;
                snd_cnt++;
            }
        }

        return 0;
    }

    public void deleteLocalFiles() {
        File filesDir=getContext().getFilesDir();
        //Log.v(TAG,"Deleting all local files");
        for (File file : filesDir.listFiles()) {
            boolean flag = getContext().deleteFile(file.getName());
            ////Log.v(TAG,"Deleted File "+file.getName() + " flag "+flag);
        }
    }

    public void deleteSingleLocalFile(String key) {
        //Log.v(TAG,"Deleting single local file "+key);
        File filesDir=getContext().getFilesDir();
        String fileName = null;
        for (File file : filesDir.listFiles()) {
            fileName = file.getName();
            if(fileName.contains(key)) {
                boolean flag = getContext().deleteFile(fileName);
                //Log.v(TAG,"Deleted File "+fileName + " flag "+flag);
                break;
            }
        }
    }


    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        ////Log.v(TAG,"Insert method called");
        String key = (String)values.get(CommonConstants.KEY_FIELD);
        String value = (String)values.get(CommonConstants.VALUE_FIELD);
        int index = findSuccesssorIndex(key);
        String coordinator = dynamoNodes.get(index).getPortNum();
        int num_nodes = dynamoNodes.size();
        int sent_cnt = 0;
        while(sent_cnt!=NUM_REPLICAS){
            Node node = dynamoNodes.get(index);
            String portNum = node.getPortNum();
            String modifiedKey = coordinator+CommonConstants.DOLLAR_SEP+portNum+
                    CommonConstants.DOLLAR_SEP+key;
            ////Log.v(TAG,"Modified key "+modifiedKey);
            if(node.getPortNum().equals(localPort)){
                //Log.v(TAG,"Inserting  " + key + " in local ");
                insertLocalKeyValue(modifiedKey,value);
            }
            else{
                ////Log.v(TAG,"Sending  " + key + " to "+ node.getPortNum());
                StringBuilder sb = new StringBuilder();
                sb.append(CommonConstants.MSG_TYPE_INSERT).append(CommonConstants.HASH_SEP)
                        .append(localPort).append(CommonConstants.HASH_SEP)
                        .append(modifiedKey).append(CommonConstants.HASH_SEP)
                        .append(value);
                String message = sb.toString();
                try {
                    String rcvdMessage=sendMessage(message,node.getPortNum());
                    ////Log.v(TAG,"Received "+rcvdMessage+" from "+node.getPortNum());
                }catch(Exception e){
                    //Log.v(TAG,"Provider insert:: Failed port "+node.getPortNum());
                }
            }
            index = (index+1) % num_nodes;
            sent_cnt++;

        }
        return null;
    }


    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        ////Log.v(TAG,"Query method called for key "+selection);
        MatrixCursor cursor=null;
        String key=selection;
        synchronized (this){
            if(CommonConstants.QUERY_LOCAL.equals(key)){
                cursor=handleLocalAllQuery();
            }else if(CommonConstants.QUERY_GLOBAL.equals(key)){
                cursor=handleGlobalAllQuery();
            }else{
                cursor=handleSingleKeyQuery(key);
            }
        }

        return cursor;
    }

    protected MatrixCursor handleLocalAllQuery(){

        MatrixCursor cursor = new MatrixCursor(new String[]{CommonConstants.KEY_FIELD,CommonConstants.VALUE_FIELD});
        File filesDir=getContext().getFilesDir();
        FileInputStream inputStream;
        try {
            for (File file : filesDir.listFiles()) {
                String filename=null;
                filename = file.getName();
                inputStream = getContext().openFileInput(filename);
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line = null;
                String string="";
                while ((line = reader.readLine()) != null) {
                    string = string + line;
                }

                inputStream.close();
                reader.close();
                String [] keyArr = filename.split(CommonConstants.DOLLAR_SEP_SPLIT);
                String key = keyArr[2];
                MatrixCursor.RowBuilder rowBuilder = cursor.newRow();
                rowBuilder.add(CommonConstants.KEY_FIELD, key);
                rowBuilder.add(CommonConstants.VALUE_FIELD, string);
                //Log.v(TAG,"Added "+ key +" : "+string+" to response");
            }
        }catch(Exception e){
            e.printStackTrace();
            //Log.v("query", "File read failed");
        }
        return cursor;
    }

    protected MatrixCursor handleGlobalAllQuery(){

        MatrixCursor cursor = new MatrixCursor(new String[]{CommonConstants.KEY_FIELD,CommonConstants.VALUE_FIELD});
        StringBuilder sb = new StringBuilder();
        sb.append(CommonConstants.MSG_TYPE_GLOBAL_QUERY).append(CommonConstants.HASH_SEP)
                .append(localPort).append(CommonConstants.HASH_SEP);
        String message=sb.toString();
        String globalDumpStr = constructLocalDump();
        for(Node node : dynamoNodes){
            if(!node.getPortNum().equals(localPort)){
                ////Log.v(TAG,"Global query *:: Forwarding "+message +" to "+ node.getPortNum());
                try {
                    // //Log.v(TAG,"Global String "+globalDumpStr);
                    String rcvdMessage=sendMessage(message, node.getPortNum());
                    ////Log.v(TAG,"Received Message "+rcvdMessage);
                    globalDumpStr = globalDumpStr + rcvdMessage;
                }catch(Exception e){
                    //Log.v(TAG, "Error occured while sending  from port "+node.getPortNum());
                }
            }
        }
        ////Log.v(TAG,"Final Global String "+globalDumpStr);
        String [] msgArr = globalDumpStr.split(CommonConstants.HASH_SEP);
        Map<String,String> responseMap = new HashMap<String,String>();
        for(int i=0;i<msgArr.length;i++){
            String keyVal = msgArr[i];
            ////Log.v(TAG,"keyVal "+ keyVal);
            String [] keyValArr = keyVal.split("\\|");
            String key = keyValArr[0];
            String val = keyValArr[1];
            if(!responseMap.containsKey(key)){
                MatrixCursor.RowBuilder rowBuilder = cursor.newRow();
                rowBuilder.add(CommonConstants.KEY_FIELD, key);
                rowBuilder.add(CommonConstants.VALUE_FIELD, val);
            }else{
                responseMap.put(key,val);
            }
        }
        return cursor;
    }


    protected String constructLocalDump(){
        StringBuilder sb = new StringBuilder();
        String filename=null;
        FileInputStream inputStream;
        File filesDir=getContext().getFilesDir();
        String string = "";
        try {
            for (File file : filesDir.listFiles()) {
                filename = file.getName();
                inputStream = getContext().openFileInput(filename);
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line = null;
                string="";
                while((line=reader.readLine())!=null){
                    string = string+line;
                }
                String [] keyArr = filename.split(CommonConstants.DOLLAR_SEP_SPLIT);
                String key = keyArr[2];
                inputStream.close();
                reader.close();
                sb.append(key).append(CommonConstants.PIPE_SEP).append(string);
                sb.append(CommonConstants.HASH_SEP);
            }
        }catch(Exception e){
            e.printStackTrace();
            //Log.v("query", "File read failed");
        }
        ////Log.v(TAG,"constructLocalDump:: Value returned is "+sb.toString());
        return sb.toString();
    }

    protected MatrixCursor handleSingleKeyQuery(String key){
        int index=findSuccesssorIndex(key);
        String rcvdMessage=null;
        int num_nodes= dynamoNodes.size();
        while(rcvdMessage==null){
            Node node = dynamoNodes.get(index);
            if(node.getPortNum().equals(localPort)){
                //Log.v(TAG," Query " + key + " to local file system");
                rcvdMessage=findValueFromKey(key);
            }
            else{
                ////Log.v(TAG,"Sending Query " + key + " to "+ node.getPortNum());
                StringBuilder sb = new StringBuilder();
                sb.append(CommonConstants.MSG_TYPE_QUERY).append(CommonConstants.HASH_SEP)
                        .append(localPort).append(CommonConstants.HASH_SEP)
                        .append(key);
                String message = sb.toString();
                try {
                    rcvdMessage=sendMessage(message,node.getPortNum());
                    ////Log.v(TAG,"Received "+rcvdMessage+" from "+node.getPortNum());
                    String [] rcvdMessageArr = rcvdMessage.split(CommonConstants.PIPE_SEP_SPLIT);
                    rcvdMessage = rcvdMessageArr[1];
                }catch(Exception e){
                    //Log.v(TAG,"Query Error:: No response from "+node.getPortNum());
                }
            }
            index = (index+1) % num_nodes;
        }


        String value = rcvdMessage;
        MatrixCursor cursor = new MatrixCursor(new String[]{CommonConstants.KEY_FIELD,CommonConstants.VALUE_FIELD});
        MatrixCursor.RowBuilder rowBuilder = cursor.newRow();
        //Log.v(TAG,"Adding key "+key+" value "+value+" in response");
        rowBuilder.add(CommonConstants.KEY_FIELD, key);
        rowBuilder.add(CommonConstants.VALUE_FIELD, value);
        return cursor;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    /**
     * Find the index of the successor node of the data
     * @param id
     * @return
     */
    public int findSuccesssorIndex(String id){
        String idHash=null;
        int index = 0;
        try{
            idHash = genHash(id);
        }catch(NoSuchAlgorithmException e){
            Log.e(TAG,"findSuccesssorIndex:: SHA-1 func not found");
            e.printStackTrace();
        }
        for(int i=0;i<dynamoNodes.size();i++){
            Node node = dynamoNodes.get(i);
            if(CommonUtil.isLessThanEqual(idHash,node.getHashVal())){
                ////Log.v(TAG, "findSuccessorIndex: "+id + " : " + idHash + " : " + node.getPortNum() + " : " + node.getHashVal());
                index=i;
                break;
            }
        }
        return index;
    }

    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    protected String convertPortNumToHashVal(String portNum){
        ////Log.v(TAG,"String port num "+portNum);
        Integer portNumInt = Integer.parseInt(portNum);
        ////Log.v(TAG,"Integer port num "+portNumInt);
        String hashParam = String.valueOf(portNumInt / 2);
        String hashedKey=null;
        try{
            hashedKey = genHash(hashParam);
        }catch(NoSuchAlgorithmException e){
            Log.e(TAG, "handleQueryMessage:: SHA-1 func not found");
            e.printStackTrace();
        }
        return hashedKey;
    }

    protected String findValueFromKey(String key){

        ////Log.v(TAG,"Query received "+key);
        File filesDir=getContext().getFilesDir();
        String fileName = null;
        for (File file : filesDir.listFiles()) {
            fileName = file.getName();
            if(fileName.contains(key)) {
                break;
            }
        }
        ////Log.v(TAG,"Querying in local filesystem "+fileName);
        FileInputStream inputStream;
        String string = "";
        try {

            inputStream = getContext().openFileInput(fileName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line=null;
            while((line=reader.readLine())!=null){
                string = string+line;
            }
            ////Log.v(TAG,"Found value "+ string);
        }catch (Exception e) {
            e.printStackTrace();
            //Log.v("query", "File read failed");
        }
        //Log.v(TAG,"Returning value "+ key+" | "+string);
        return string;
    }

    protected void insertLocalKeyValue(String key,String value){
        ////Log.v(TAG,"Inserting in local filesystem "+key+":"+value);
        String filename = key;
        String string = value;

        FileOutputStream outputStream;

        try {
            outputStream = getContext().openFileOutput(filename, Context.MODE_PRIVATE);
            outputStream.write(string.getBytes());
            outputStream.close();
        } catch (Exception e) {
            Log.e(TAG, "File write failed");
        }
        //Log.v(TAG,"insert:: "+filename+"value "+string+" inserted");
    }





    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {
        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            Socket clientSocket = null;


            try {
                /**
                 * Infinite loop to receive messages from the client side
                 * continuously
                 */
                while (true) {
                    /**
                     * Open a secondary socket for the client and read data
                     * Reference - http://docs.oracle.com/javase/7/docs/api/java/io/BufferedReader.html
                     */
                    clientSocket = serverSocket.accept();
                    ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
                    String message = (String)in.readObject();
                    //Log.v(TAG, "Received mesage " + message);

                    Message msg = new Message(message,clientSocket);

                    new HandleMessageTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,msg);

                }
            } catch (IOException e) {
                Log.e(TAG, "IOException:: Cannot receive data");
            } catch (Exception e){
                Log.e(TAG, "Excetion:: Cannot receive data");
            }
            return null;
        }
    }


    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            String destPort = msgs[1];
            String msgToSend = msgs[0];

            //unicastMessage(msgToSend, destPort);
            return null;
        }

    }

    private class RecoverTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void...params) {
            int num_nodes = dynamoNodes.size();
            int localIndex = getLocalIndex();
            int succIndex = (num_nodes+localIndex + 1) % num_nodes;
            int predIndex = (num_nodes+localIndex - 1) % num_nodes;
            int predpredIndex = (num_nodes+localIndex - 2) % num_nodes;
            String recoveryResp = "";
            //Log.v(TAG,"LocalIndex:"+localIndex+" predpredIndex:"+predpredIndex+" predIndex:"+predIndex+" succIndex:"+succIndex);
            //send message to predpredNode
            //Log.v(TAG,"Sending recovery message to predecessor of predecessor");
            String predPredResp = sendRecoveryMsg(predpredIndex,CommonConstants.MSG_TYPE_RECOVER_SUCC);
            recoveryResp = recoveryResp + predPredResp;

            //send message to predNode
            //Log.v(TAG,"Sending recovery message to predecessor");
            String predResp = sendRecoveryMsg(predIndex,CommonConstants.MSG_TYPE_RECOVER_SUCC);
            recoveryResp = recoveryResp + predResp;

            //send message to predNode
            //Log.v(TAG,"Sending recovery message to successor");
            String succResp = sendRecoveryMsg(succIndex,CommonConstants.MSG_TYPE_RECOVER_PRED);
            recoveryResp = recoveryResp + succResp;



            //Log.v(TAG,"Final Recovery String "+ recoveryResp);
            String [] msgArr = recoveryResp.split(CommonConstants.HASH_SEP);
            for(int i=0;i<msgArr.length;i++){
                String keyVal = msgArr[i];
                //Log.v(TAG,"keyVal "+ keyVal);
                String [] keyValArr = keyVal.split("\\|");
                String key = keyValArr[0];
                String value = keyValArr[1];
                if(!newInserts.containsKey(key)){
                    insertLocalKeyValue(key,value);
                }
            }
            newInserts = new HashMap<>();
            recoveryInProgress = false;
            return null;
        }


        protected String sendRecoveryMsg(int index,String msgType){
            Node node = dynamoNodes.get(index);
            StringBuilder sb = new StringBuilder();
            sb.append(msgType).append(CommonConstants.HASH_SEP).append(localPort);
            String message = sb.toString();
            //Log.v(TAG,"Sending "+message + " to "+ node.getPortNum());
            String rcvdMsg="";
            try {
                rcvdMsg = sendMessage(message, node.getPortNum());
            }catch(Exception e){
                //Log.v(TAG,"Exception occured during recovery from "+node.getPortNum());
            }
            return rcvdMsg;
        }


        protected int getLocalIndex(){
            int index = 0 ;
            for(int i=0;i<dynamoNodes.size();i++){
                if(dynamoNodes.get(i).getPortNum().equals(localPort)){
                    index = i;
                    break;
                }
            }
            return index;
        }

    }

    /***
     * HandleMessageTask is an AsyncTask that should handle the string received by the server
     * It is created by ClientTask.executeOnExecutor() call whenever the 'Send'
     * Button is clicked
     * @author pnandi
     *
     */
    private class HandleMessageTask extends AsyncTask<Message, Void, Void> {
        @Override
        protected Void doInBackground(Message... msgs) {
            Message msgObj = msgs[0];
            String message = msgObj.message;
            Socket socket = msgObj.socket;
            String msgArr[]=message.split(CommonConstants.HASH_SEP);
            String msgType = msgArr[0];
            if(msgType.equals(CommonConstants.MSG_TYPE_INSERT)){
                ////Log.v(TAG,"Message type INSERT received "+ message + "from "+msgArr[1]);
                handleInsertMessage(message);
                try {
                    ////Log.v(TAG,"Sending ACK for INSERT message");
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    out.writeObject(CommonConstants.MSG_TYPE_ACK);
                    out.flush();
                    out.close();
                    ////Log.v(TAG,"ACK sent");
                }catch(IOException e){
                    e.printStackTrace();
                    //Log.v(TAG,"Error occured while sending the ACK message");
                }
            }else if(msgType.equals(CommonConstants.MSG_TYPE_QUERY) && !recoveryInProgress){
                ////Log.v(TAG,"Message type QUERY received "+ message + "from "+msgArr[1]);
                String sndMsg=handleQueryMessage(message);
                try {
                    //Log.v(TAG,"Sending query response message "+sndMsg);
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    out.writeObject(sndMsg);
                    out.flush();
                    out.close();
                    ////Log.v(TAG,"query response message sent");
                }catch(IOException e){
                    e.printStackTrace();
                    //Log.v(TAG,"Error occured while sending the Query Response message");
                }
            }else if(msgType.equals(CommonConstants.MSG_TYPE_GLOBAL_QUERY)){
                ////Log.v(TAG,"Message type GLOBAL QUERY received "+ message + "from "+msgArr[1]);
                String sndMsg=constructLocalDump();
                try {
                    ////Log.v(TAG,"Sending query response message "+sndMsg);
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    out.writeObject(sndMsg);
                    out.flush();
                    out.close();
                    //Log.v(TAG,"GLOBAL query response message sent");
                }catch(IOException e){
                    e.printStackTrace();
                    //Log.v(TAG,"Error occured while sending the Query Response message");
                }
            }else if(msgType.equals(CommonConstants.MSG_TYPE_DELETE)){
                ////Log.v(TAG,"Message type DELETE received "+ message + "from "+msgArr[1]);
                handleDeleteMessage(message);
                try {
                    ////Log.v(TAG,"Sending ACK for DELETE message");
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    out.writeObject(CommonConstants.MSG_TYPE_ACK);
                    out.flush();
                    out.close();
                    ////Log.v(TAG,"ACK sent");
                }catch(IOException e){
                    e.printStackTrace();
                    //Log.v(TAG,"Error occured while sending the ACK message");
                }
            }else if(msgType.equals(CommonConstants.MSG_TYPE_RECOVER_SUCC)){
                //Log.v(TAG,"Successor "+ msgArr[1] +"has recovered");
                String resp = handleRecoverSuccessor(message);
                try {
                    ////Log.v(TAG,"Sending ACK for DELETE message");
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    out.writeObject(resp);
                    out.flush();
                    out.close();
                    ////Log.v(TAG,"ACK sent");
                }catch(IOException e){
                    e.printStackTrace();
                    //Log.v(TAG,"Error occured while sending the ACK message");
                }
            }else if(msgType.equals(CommonConstants.MSG_TYPE_RECOVER_PRED)){
                //Log.v(TAG,"Predecessor "+ msgArr[1] +"has recovered");
                String resp = handleRecoverPredecessor(message);
                try {
                    ////Log.v(TAG,"Sending ACK for DELETE message");
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    out.writeObject(resp);
                    out.flush();
                    out.close();
                    ////Log.v(TAG,"ACK sent");
                }catch(IOException e){
                    e.printStackTrace();
                    //Log.v(TAG,"Error occured while sending the ACK message");
                }
            }
            return null;
        }


        /**
         * Called by the receiving nodes to insert message
         * @param message
         */
        protected void handleInsertMessage(String message){
            //Log.v(TAG,"Insert message received "+ message);
            String [] msgArr = message.split(CommonConstants.HASH_SEP);
            String filename = msgArr[2];
            String string = msgArr[3];
            if(recoveryInProgress){
                //Log.v(TAG,"Adding "+filename+" : "+string+"to newInserts");
                newInserts.put(filename,string);
            }

            FileOutputStream outputStream;

            try {
                outputStream = getContext().openFileOutput(filename, Context.MODE_PRIVATE);
                outputStream.write(string.getBytes());
                outputStream.close();
            } catch (Exception e) {
                Log.e(TAG, "File write failed");
            }
            //Log.v(TAG, "insert:: " + filename + "value " + string + " inserted");
        }

        protected String handleQueryMessage(String message){
            //Log.v(TAG,"Query message received "+ message);
            String [] msgArr = message.split(CommonConstants.HASH_SEP);
            String key = msgArr[2];
            File filesDir=getContext().getFilesDir();
            String fileName = null;
            FileInputStream inputStream;
            String string="";
            for (File file : filesDir.listFiles()) {
                fileName = file.getName();
                if(fileName.contains(key)) {
                    break;
                }
            }
            try{
                inputStream = getContext().openFileInput(fileName);
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line = null;
                while ((line = reader.readLine()) != null) {
                    string = string + line;
                }

                inputStream.close();
                reader.close();

            }catch(IOException e){

            }
            ////Log.v(TAG, "handleQueryMessage:: Value found " + string);
            String ret = key + CommonConstants.PIPE_SEP + string;
            //Log.v(TAG, "handleQueryMessage::" + ret +" returned ");
            return ret;
        }

        /**
         * Called when 'DELETE' is received
         * @param message
         */
        protected void handleDeleteMessage(String message){
            ////Log.v(TAG,"Inside handleDeleteMessage:: ");
            String [] msgArr = message.split(CommonConstants.HASH_SEP);
            String senderPort = msgArr[1];
            String key = msgArr[2];
            if(CommonConstants.QUERY_GLOBAL.equals(key)){
                //Log.v(TAG,"Deleting local files for *");
                deleteLocalFiles();
            }else{
                deleteSingleLocalFile(key);
            }
        }


        /**
         * Called when one of the successors has recovered
         * @param message
         */
        protected String handleRecoverSuccessor(String message){
            String [] msgArr = message.split(CommonConstants.HASH_SEP);
            String succPort = msgArr[1];
            File filesDir=getContext().getFilesDir();
            FileInputStream inputStream = null;
            StringBuilder sb = new StringBuilder();
            try {
                for (File file : filesDir.listFiles()) {
                    String filename = file.getName();
                    String [] fileNameArr = filename.split(CommonConstants.DOLLAR_SEP_SPLIT);
                    String coordinator = fileNameArr[0];
                    String key = fileNameArr[2];
                    if(coordinator.equals(localPort)){
                        inputStream = getContext().openFileInput(filename);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                        String line = null;
                        String string="";
                        while ((line = reader.readLine()) != null) {
                            string = string + line;
                        }
                        //Log.v(TAG,"Recovered message:: "+filename+CommonConstants.PIPE_SEP+string);
                        sb.append(localPort).append(CommonConstants.DOLLAR_SEP).append(succPort)
                                .append(CommonConstants.DOLLAR_SEP).append(key).append(CommonConstants.PIPE_SEP)
                                .append(string);
                        sb.append(CommonConstants.HASH_SEP);
                        //Log.v(TAG,"Modified Recovered message:: "+sb.toString());
                        inputStream.close();
                        reader.close();
                    }
                }
            }catch(Exception e){
                e.printStackTrace();
                //Log.v(TAG,"handleRecoverSuccessor:File read failed");
            }
            //Log.v(TAG,"handleRecoverSuccessor:: "+sb.toString());
            return sb.toString();
        }


        /**
         * Called when one of the successors has recovered
         * @param message
         */
        protected String handleRecoverPredecessor(String message){
            String [] msgArr = message.split(CommonConstants.HASH_SEP);
            String predPort = msgArr[1];
            File filesDir=getContext().getFilesDir();
            FileInputStream inputStream = null;
            StringBuilder sb = new StringBuilder();
            try {
                for (File file : filesDir.listFiles()) {
                    String filename = file.getName();
                    String [] fileNameArr = filename.split(CommonConstants.DOLLAR_SEP_SPLIT);
                    String coordinator = fileNameArr[0];
                    String key = fileNameArr[2];
                    if(coordinator.equals(predPort)){
                        inputStream = getContext().openFileInput(filename);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                        String line = null;
                        String string="";
                        while ((line = reader.readLine()) != null) {
                            string = string + line;
                        }
                        //Log.v(TAG,"Recovered message:: "+filename+CommonConstants.PIPE_SEP+string);
                        sb.append(predPort).append(CommonConstants.DOLLAR_SEP).append(predPort)
                                .append(CommonConstants.DOLLAR_SEP).append(key).append(CommonConstants.PIPE_SEP)
                                .append(string);
                        sb.append(CommonConstants.HASH_SEP);
                        //Log.v(TAG,"Modified Recovered message:: "+sb.toString());
                        inputStream.close();
                        reader.close();
                    }
                }
            }catch(Exception e){
                e.printStackTrace();
                //Log.v("handleRecoverPredecessor", "File read failed");
            }
            //Log.v(TAG,"handleRecoverPredecessor:: "+sb.toString());
            return sb.toString();
        }

    }


}