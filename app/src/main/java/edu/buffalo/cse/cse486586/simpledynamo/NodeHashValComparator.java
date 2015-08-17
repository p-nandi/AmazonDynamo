package edu.buffalo.cse.cse486586.simpledynamo;

import java.util.Comparator;

/**
 * Created by phantom on 3/24/15.
 */
public class NodeHashValComparator implements Comparator<Node>{

    public int compare(Node node1,Node node2){
        /*int compareVal = node1.getHashVal().compareTo(node1.getHashVal());
        if(compareVal<=0){
            return 1;
        }
        else{
            return -1;
        }*/
        return node1.getHashVal().compareTo(node2.getHashVal());
    }
}
