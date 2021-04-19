import java.util.*;
import java.io.Serializable;

//A serializable class that holds the attributes of the Incoming and Outgoing messages
public class Message implements Serializable{
  //Type of the message. E.g SENDTOKEN, SENDREQUEST, CREATE, DELETE, 
  String messageType;
  //The Process ID of the message sender
  int processId;
  //FilInfo object holding all the information related to the File resource.
  //This object will act like a token in the Raymond's Algorithm
  FileInfo fileInfo;

  public Message(String messagetype, FileInfo fileinfo, int processid) {
    this.messageType = messagetype;
    this.fileInfo = fileinfo;
    this.processId = processid;
  }

  public FileInfo getFileInfo(){
    return this.fileInfo;
  }

  public int getProcessId(){
    return this.processId;
  }

  public String getMessageType(){
    return this.messageType;
  }

  public void setFileInfo(FileInfo newInfo){
    this.fileInfo = newInfo;
  }
  public void setProcessId(int id){
    this.processId = id;
  }

  public void setMessageType(String mType){
    this.messageType = mType;
  }
}
