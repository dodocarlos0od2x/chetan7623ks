import java.net.*;
import java.io.*;
import java.util.*;

//A serializable class that holds the information related to the File Descriptor.
//The object of this class can be considered as a file token in the Raymond's Algorithm  
public class FileInfo implements Serializable{
  //Name of the File resource
  String fileName;
  //The current File contents
  String content;
  //The pointer that poits to the Process ID of the Process that points to the location of token in the network
  int holder;
  //The request Queue that holds the list of the requesting Processes. Holds the Process IDs
  List<Integer> processQueue;
  //Flag if the Resource is being used used currently by the holder of the token
  boolean usingResource;
  //Flag if the neighbor has requested for the token
  boolean asked;
  //Holds the type of operation the requestor wants to perform on the Resource File
  String requestType;

  //Constructor that initializes the members
  public FileInfo(String file, int identifier) {
    fileName = file;
    content = "";
    holder = identifier;
    processQueue = new ArrayList<Integer>();
    usingResource = false;
    asked = false;
    requestType = "";
  }

  public String getFileName(){
    return this.fileName;
  }

  public String getContent(){
    return this.content;
  }

  public int getHolder(){
    return this.holder;
  }

  public List<Integer> getQueue(){
    return this.processQueue;
  }

  public boolean getUsingResource(){
    return this.usingResource;
  }

  public boolean getAsked(){
    return this.asked;
  }

  public String getRequestType(){
    return this.requestType;
  }


  public void setContent(String added){
    this.content = added;
  }

  public void setHolder(int newId){
    this.holder = newId;
  }

  public void setQueue(List<Integer> newQueue){
    this.processQueue = newQueue;
  }

  public void setUsingResource(boolean newValue){
    this.usingResource = newValue;
  }

  public void setAsked(boolean newValue){
    this.asked = newValue;
  }

  public void setRequestType(String newValue){
    this.requestType = newValue;
  }
}
