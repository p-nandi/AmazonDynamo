package edu.buffalo.cse.cse486586.simpledynamo;

public class Node {

    public Node(String portNum,String hashVal,Boolean isActive){
        this.portNum = portNum;
        this.hashVal = hashVal;
        this.isActive = isActive;
    }

    public Node(String portNum,String hashVal){
        this.portNum = portNum;
        this.hashVal = hashVal;
    }

    private Boolean isActive;
	/**
     * Port number of the node
     */
    private String portNum;

    /**
     * HashVal of the node
     */
    private String hashVal;

    /**
     * Predecessor of the node
     */
    private Node predecessor;

    /**
     * Successor of the node
     */
    private Node successor;

	public String getPortNum() {
		return portNum;
	}

	public void setPortNum(String portNum) {
		this.portNum = portNum;
	}

	public String getHashVal() {
		return hashVal;
	}

	public void setHashVal(String hashVal) {
		this.hashVal = hashVal;
	}

	public Node getPredecessor() {
		return predecessor;
	}

	public void setPredecessor(Node predecessor) {
		this.predecessor = predecessor;
	}

	public Node getSuccessor() {
		return successor;
	}

	public void setSuccessor(Node successor) {
		this.successor = successor;
	}

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(String nodeId) {
        this.isActive = isActive;
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((hashVal == null) ? 0 : hashVal.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Node other = (Node) obj;
        if (hashVal == null) {
            if (other.hashVal != null)
                return false;
        } else if (!hashVal.equals(other.hashVal))
            return false;
        return true;
    }



}
