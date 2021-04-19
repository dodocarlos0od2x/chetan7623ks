import java.net.*;
import java.io.*;
import java.util.*;
import java.io.Console;

//Class that implements the Raymond's Algorithm for synchronizing access to the file resources in a distributed system
public class Process extends Thread {
   //Static volatile variables to make sure that all the server and the main thread use the updated values everytime.

   //The socket that will listen to all the incoming messages.
   static volatile ServerSocket serverSocket;
   //The list of processes that are connected to this Process in the network
   static volatile List<Integer> neighbors;
   //The process ID for this Process
   static volatile int id;
   //The port number on which this Server thread will listen to incoming messages
   static volatile int port;
   //The IP Address of instance on which this Process is hosted
   static volatile String ipaddress;
   //A map that will hold all the attributes like Request Queue, Holder, FilaName, Content, etc.
   //The key is the filename and the Value is the object of type FileInfo
   static volatile HashMap<String, FileInfo> fileDescriptor;
   //The map that stores the IP Addresses of all the neighbors
   //The key is the Process ID and value is the IP Address of the instance on which the Process is hosted
   static volatile HashMap<Integer, String> ipAddressMap;
   //The map that stores the IP Addresses of all the neighbors
   //The key is the Process ID and value is the port number of the instance on which the Process is hosted
   static volatile HashMap<Integer, Integer> portMap;
   //This will temporarily hold the contents that are to be appended to a specific file.
   static volatile String appendContent;

   //create the server socket with the port number set from the config file
   public Process(int port, int id) throws IOException {
      serverSocket = new ServerSocket(port);
      //set timeout in case of server inactivity
      serverSocket.setSoTimeout(100000000);
   }

   //Default Process constructor which will initialize the hash maps
   //which will be used to keep track of key information.
   public Process() {
     fileDescriptor = new HashMap<String, FileInfo>();
     ipAddressMap = new HashMap<Integer, String>();
     portMap = new HashMap<Integer, Integer>();
   }

   //read the file and get the approrpriate neighbors for each process
   private static List<Integer> readFile(File fin, List<Integer> neighbors, int id) throws IOException {
      FileInputStream fis = new FileInputStream(fin);

      //Construct BufferedReader from InputStreamReader
      BufferedReader br = new BufferedReader(new InputStreamReader(fis));

      String line = null;
      while ((line = br.readLine()) != null) {
        //read every line from the network tree file and call getNrighbors to set the 
         neighbors = getNeighbors(line, neighbors, id);
      }


      br.close();

      //Return the list of neighbors for this Process
      return neighbors;
   }

   //file to read information about sockets
   private static Process readSocketDetails(File fin, Process obj) throws IOException {
      FileInputStream fis = new FileInputStream(fin);
      //Holds the Process ID, Port number and IP Address read from the SocketConfig file.
      String[] tokenInfo;

      //Construct BufferedReader from InputStreamReader
      BufferedReader br = new BufferedReader(new InputStreamReader(fis));
      //place values into hash maps through use of while loop
      String line = null;

      while ((line = br.readLine()) != null) {
        tokenInfo = line.split(",");
        //First token is the Process ID
        int process =  Integer.parseInt(tokenInfo[0]);
        //If Process ID is same as the Process ID of this Process, set the port number for this process
        if(process == obj.id){
          //Port number is the third token
          obj.port = Integer.parseInt(tokenInfo[2]);
        }
        //IP Address is the second token
        String ipAddress = tokenInfo[1];
        int portNum = Integer.parseInt(tokenInfo[2]);

        //Put the IP Address and Port Number for the neighbor in the respective hash maps
        obj.ipAddressMap.put(process, ipAddress);
        obj.portMap.put(process, portNum);
      }
      br.close();

      return obj;
   }

   //function to get the neighbors for each process
   public static List<Integer> getNeighbors(String line, List<Integer> neighbors, int id){
      String[] processes = new String[2];
      int neighbor;

      line = line.replace("(", "");
      line = line.replace(")", "");

      processes = line.split(",");
      //Set Process1 to first token
      int process1 =  Integer.parseInt(processes[0]);
      //Set Proces2 to second token
      int process2 = Integer.parseInt(processes[1]);
      //If id of the process does not match either of the first or the second token, 
      //do not add the process to list of neighbors 
      if(id != process1 && id != process2){
        return neighbors;
      }
      //If the first token matches the ID of this Process, set neighbor to the Second Token.
      else if(id == process1){
        neighbor = process2;
      }
      //If the second token matches the ID of this Process, set neighbor to the First Token.
      else {
        neighbor = process1;
      }

      boolean alreadyPresent = false;
      //ensure that neighbor is not already in the list.
      for(int i = 0; i < neighbors.size(); i++){
        if(neighbors.get(i) == neighbor){
          alreadyPresent = true;
        }
      }
      //If neighbor is not present in the list of neighbors, add it
      if(!alreadyPresent){
        neighbors.add(neighbor);
      }

      //return the list of neighbors for this process.
      return neighbors;
   }

   //assign token for access to a file for a particular process.
   public boolean assignToken(String fileName){
      FileInfo fileInfo = this.fileDescriptor.get(fileName);
      int holder = fileInfo.getHolder();
      List<Integer> currentQueue = fileInfo.getQueue();
      //check if the holder of token is current process, resource not being used
      //and queue contains at least one process
      if(holder == this.id && !fileInfo.getUsingResource() && currentQueue.size() > 0){
        holder = currentQueue.remove(0);
        fileInfo.setAsked(false);
        //if upon removal of element from queue check to see if
        //holder is current process
        if(holder == this.id) {
          fileInfo.setUsingResource(true);
          this.fileDescriptor.put(fileName, fileInfo);
          //depending on the type of request, delete, read or append to file
          if(fileInfo.getRequestType() == "DELETE"){
            deleteFile(fileName);
          }
          else if(fileInfo.getRequestType() == "READ"){
            readFile(fileName);
          }
          else if(fileInfo.getRequestType() == "APPEND"){
            appendFile(fileName);
          }
          //Return true when the Process gains the access to the resource and executes the command
          return true;
        }
        else{
          //If this process has the token but is not at the top pf the request queue, send the token to the requestor.
          //Send holder as the one receiving the resource token. Will updatw the token pointer
          fileInfo.setHolder(holder);
          this.fileDescriptor.put(fileName, fileInfo);
          System.out.println("#LOGGER: SET HOLDER to : " + holder);
          System.out.println("#LOGGER: SENDING TOKEN TO HOLDER to " + holder + " with Content: " + this.fileDescriptor.get(fileName).getContent());
          //Build a new outgoing message with the Message type set to SENDTOKEN
          Message sentMessage = new Message("SENDTOKEN", fileInfo, this.id);
          int portNumber = portMap.get(holder);
          String ipAddress = ipAddressMap.get(holder);
          //Call client method to send the message to the required Process
          client(portNumber, ipAddress, sentMessage);
        }
      }
      //Return true when the Process either releaves control over the File token or send the token to other Prcocess
      return false;
   }

   //send the token request
   public void sendRequest(String fileName){
     FileInfo fileInfo = this.fileDescriptor.get(fileName);
     if(fileInfo == null){
       return;
     }
     int holder = fileInfo.getHolder();
     List<Integer> currentQueue = fileInfo.getQueue();
     boolean asked = fileInfo.getAsked();
     if(holder != this.id && currentQueue.size() > 0 && !asked){
       Message requestMessage = new Message("SENDREQUEST", fileInfo, this.id);
       fileInfo.setAsked(true);
       //update fileDescriptor hash map
       this.fileDescriptor.put(fileName, fileInfo);
       int portNumber = portMap.get(holder);
       String ipAddress = ipAddressMap.get(holder);
       System.out.println("#LOGGER: Sending Request to " + holder);
       client(portNumber, ipAddress, requestMessage);
     }
   }

   //function to request the resource token.
   public boolean requestResource(String fileName){
     FileInfo fileInfo = this.fileDescriptor.get(fileName);
     //obtain the current queue
     List<Integer> currentQueue = fileInfo.getQueue();
     //add process to queue
     currentQueue.add(this.id);
     //update the token queue
     fileInfo.setQueue(currentQueue);
     this.fileDescriptor.put(fileName, fileInfo);
     boolean isAssigned = assignToken(fileName);
     //send the request
     sendRequest(fileName);
     if(isAssigned){
       return true;
     }
     else{
       return false;
     }
   }

   //release the resource by setting boolean variable
   //setUsingResource to false
   public void releaseResource(String fileName){
     FileInfo fileInfo = this.fileDescriptor.get(fileName);
     fileInfo.setUsingResource(false);
     this.fileDescriptor.put(fileName, fileInfo);
     assignToken(fileName);
     sendRequest(fileName);
   }

   //function to handle the receiving of a request
   public void receiveRequest(int processId, String fileName){
     FileInfo fileInfo = this.fileDescriptor.get(fileName);
     int neighbor = processId;
     List<Integer> currentQueue = fileInfo.getQueue();
     currentQueue.add(neighbor);
     fileInfo.setQueue(currentQueue);
     this.fileDescriptor.put(fileName, fileInfo);
     assignToken(fileName);
     sendRequest(fileName);
   }

   //function to handle receiving the token
   public void receiveToken(String fileName){
     FileInfo fileInfo = this.fileDescriptor.get(fileName);
     fileInfo.setHolder(this.id);
     this.fileDescriptor.put(fileName, fileInfo);
     boolean result = assignToken(fileName);
     if(!result){
        sendRequest(fileName);
     }
   }

   //function to delete a file resource
   public boolean deleteFile(String fileName){
     if(this.fileDescriptor.containsKey(fileName)){
       //create and send a message to all neighbors informing them the file is deleted.
       Message msg = new Message("DELETE", this.fileDescriptor.get(fileName), this.id);
       for(int neighbor : this.neighbors){
           this.client(this.portMap.get(neighbor), this.ipAddressMap.get(neighbor), msg);
       }
       this.fileDescriptor.remove(fileName);
       System.out.println("#LOGGER: File " + fileName + " is deleted");
       return true;
     }
     return false;
   }

   //read the file reesorce
   public boolean readFile(String fileName){
     //check to make sure file exists
     if(this.fileDescriptor.containsKey(fileName)){
       System.out.println("Read the contents from file " + fileName);
       System.out.println("Contents: " + this.fileDescriptor.get(fileName).getContent());
       FileInfo currentFile = this.fileDescriptor.get(fileName);
       //change request type to default setting
       currentFile.setRequestType("");
       this.fileDescriptor.put(fileName, currentFile);
       releaseResource(fileName);
       return true;
     }
     return false;
   }

   //append content to a file resource
   public boolean appendFile(String fileName){
     //check to make sure file exists
     if(this.fileDescriptor.containsKey(fileName)){
       String originalContent = this.fileDescriptor.get(fileName).getContent();
       originalContent += this.appendContent;
       FileInfo currentFile = this.fileDescriptor.get(fileName);
       //set the content of the file to the new content.
       currentFile.setContent(originalContent);
       currentFile.setRequestType("");
       this.fileDescriptor.put(fileName, currentFile);
       System.out.println("The appended file " + fileName + " is now: " + this.fileDescriptor.get(fileName).getContent());
       releaseResource(fileName);
       return true;
     }
     return false;
   }

   //display the prompt to the user giving them all the different options.
   public void displayPrompt(){
      Console console = System.console();
      System.out.println("\n*********************************************");
      System.out.println("Type 1 if you wish to create a file.");
      System.out.println("Type 2 if you wish to delete a file.");
      System.out.println("Type 3 if you wish to read a file.");
      System.out.println("Type 4 if you wish to append to a file.\n");
      System.out.println("*********************************************");

      console.flush();
      String i = console.readLine();
      console.flush();
      //handle different input with different cases
      switch(i){
          //if 1 entered, create a file
          case "1":
             System.out.println("Please enter the name of the file");
             console.flush();
             String fileName = console.readLine();
             console.flush();

             System.out.println("*********************************************");

             if(this.fileDescriptor.containsKey(fileName)){
               System.out.println("File already exists!");
             }

             else{
               System.out.println(fileName + ": Sending File info to neighbors...");
               FileInfo newFile = new FileInfo(fileName, id);
               this.fileDescriptor.put(fileName, newFile);
               Message message = new Message("CREATE", newFile, id);
               for(int n:neighbors){
                  this.client(this.portMap.get(n), this.ipAddressMap.get(n), message);
               }
             }
           break;
           //if 2 entered delete the file but check to make sure it exists.
           case "2":
              System.out.println("Please enter the name of the file");
              console.flush();
              fileName = console.readLine();
              console.flush();
              if(!this.fileDescriptor.containsKey(fileName)){
                System.out.println("File does not exist!");
              }
              else{
                FileInfo fileInfo = this.fileDescriptor.get(fileName);
                fileInfo.setRequestType("DELETE");
                this.fileDescriptor.put(fileName, fileInfo);
                boolean isAssigned = requestResource(fileName);
                if(isAssigned){
                    System.out.println("Deleted file sucessfully");
                  }
                  //if isAssigned false, means we have to wait for access to file.
                  else{
                    System.out.println("You don't have access to the file! Please wait...");
                  }
                }
            break;
          //if 3 entered, read from file if it exists.
           case "3":
            System.out.println("Please enter the name of the file");
            console.flush();
            fileName = console.readLine();
            console.flush();
            if(!this.fileDescriptor.containsKey(fileName)){
              System.out.println("File does not exist!");
            }

            else{
              FileInfo fileInfo = this.fileDescriptor.get(fileName);
              fileInfo.setRequestType("READ");
              this.fileDescriptor.put(fileName, fileInfo);
              //Request access to the resource
              boolean isAssigned = requestResource(fileName);
              //If file read, print success message
              if(isAssigned){
                System.out.println("Read file sucessfully");
              }
              //Process has not yet gained access to the token, ask user to wait until it get the access
              else{
                System.out.println("You don't have access to the file! Please wait...");
              }
            }
           break;

           //if 4 entered, append to file if it exists.
           case "4":
            System.out.println("Please enter the name of the file");
            console.flush();
            fileName = console.readLine();
            console.flush();
            //If file entry is not in file descriptor, print File not found error, else run Algorithm to gain access
            //and Append to the file
            if(!this.fileDescriptor.containsKey(fileName)){
              System.out.println("File does not exist!");
            }
            else{
              System.out.println("Please enter the contents you wish to append to the file");
              console.flush();
              String contents = console.readLine();
              console.flush();
              this.appendContent = contents;
              FileInfo fileInfo = this.fileDescriptor.get(fileName);
              fileInfo.setRequestType("APPEND");
              this.fileDescriptor.put(fileName, fileInfo);
              //Request access to the resource
              boolean isAssigned = requestResource(fileName);
              //If file appended, print success message
              if(isAssigned){
                System.out.println("Appended contents to file sucessfully");
              }
              //Process has not yet gained access to the token, ask user to wait until it get the access
              else{
                System.out.println("You don't have access to the file! Please wait...");
              }
            }
            break;

            default:
              System.out.println("Invalid input! Please enter your choice again.");
            break;
        }
   }

   //parse the messages received by process.
   //Based on the type of the message, calls the approproate method implemeting the Raymond's Algorithm
   public void parseMessage(Message message){
     int messageProcessId = 0;
     String fileName = "";

      switch(message.getMessageType()){
        //if CREATE is message, create the file and then inform the other neighbors about the newly created file.
        case "CREATE":
          FileInfo fileInfo = message.getFileInfo();
          messageProcessId = message.getProcessId();
          message.setProcessId(this.id);
          fileInfo.setHolder(messageProcessId);
          message.setFileInfo(fileInfo);
          this.fileDescriptor.put(fileInfo.getFileName(), fileInfo);
          System.out.println("#LOGGER: Created new file: " + fileInfo.getFileName());
          System.out.println("#LOGGER: New file holder: " + fileInfo.getHolder());

          for(int neighbor : this.neighbors){

            if(neighbor != messageProcessId){
              this.client(this.portMap.get(neighbor), this.ipAddressMap.get(neighbor), message);
            }

          }
          break;

        //if DELETE is message type, delted the entry from FileDecriptor map and 
        //inform all neighbors so they can delete
        //the file from their own hash maps.
        case "DELETE":
          FileInfo fInfo = message.getFileInfo();
          messageProcessId = message.getProcessId();
          message.setProcessId(this.id);
          fInfo.setHolder(messageProcessId);
          message.setFileInfo(fInfo);
          for(int neighbor : this.neighbors){
            if(neighbor != messageProcessId){
              this.client(this.portMap.get(neighbor), this.ipAddressMap.get(neighbor), message);
            }
          }
          this.fileDescriptor.remove(fInfo.getFileName());
          System.out.println("#LOGGER: Deleted file...");
          break;

        //if message type is SENDTOKEN, it means that this Process has received the Token from the sender 
        //Update the file content and then call
        //receiveToken function.
        case "SENDTOKEN":
          fileName = message.getFileInfo().getFileName();
          FileInfo currentFile = this.fileDescriptor.get(fileName);
          currentFile.setContent(message.getFileInfo().getContent());
          this.fileDescriptor.put(fileName, currentFile);
          receiveToken(fileName);
          break;
        //If message type is SENDREQUEST, it means that this Process has received the Token request from the sender 
        //call receiveRequest function.
        case "SENDREQUEST":
          messageProcessId = message.getProcessId();
          fileName = message.getFileInfo().getFileName();
          receiveRequest(messageProcessId, fileName);
          break;

      }
      return;
   }
   //run function will continuously run the ServerSocket.
   //Listens to all the incoming messages
   public void run() {
      while(true) {
         try {
            Socket server = serverSocket.accept();
            ObjectInputStream in = new ObjectInputStream(server.getInputStream());
            try{
              Object object = in.readObject();
              final Message inMessage = (Message) object;

              //When a message is received, send the message to the parseMessage function.
              parseMessage(inMessage);

              System.out.println("\n*********************************************");
              System.out.println("Type 1 if you wish to create a file.");
              System.out.println("Type 2 if you wish to delete a file.");
              System.out.println("Type 3 if you wish to read a file.");
              System.out.println("Type 4 if you wish to append to a file.\n");
              System.out.println("*********************************************");
            }
            catch(ClassNotFoundException e){
              e.printStackTrace();
            }
            DataOutputStream out = new DataOutputStream(server.getOutputStream());

         }catch(SocketTimeoutException s) {
            System.out.println("Socket timed out!");
            break;
         }catch(IOException e) {
            e.printStackTrace();
            break;
         }
      }
   }

   //client function called to communicate with other processes.
   //It sends out all the outgoing messages
   /*Argument: port- port on which the recipient is running the server socket
               serverName- IP Address of the instance that is running the recipient Process
               message- The Message object that holds the message
  */
   public void client(int port, String serverName, Message message) {
      try {
         //Create a new client socket that will send messages to the required Process.
         Socket client = new Socket(serverName, port);

         ObjectOutputStream objectOutput = new ObjectOutputStream(client.getOutputStream());
         objectOutput.writeObject(message);

         InputStream inFromServer = client.getInputStream();
         DataInputStream in = new DataInputStream(inFromServer);
         client.close();
      }catch(IOException e) {
         e.printStackTrace();
      }
   }


   //Main method.
   //Entry point for the program execution
   public static void main(String [] args) {
      boolean result = true;
      int id = Integer.parseInt(args[0]);
      //read the contents of the input text and then obtain important information. 
      //input.txt will hold th informaton related to the network tree
      File fin = new File("input.txt");
      List<Integer> neighbors = new ArrayList<Integer>();

      try {
         Process processObj = new Process();
         //Set the Process ID to the first command line arguement
         processObj.id = id;

         //Read the IP Adresses and the Port neighbors from the SocketDetails.txt file and 
         //set the configuration for the neighbors and this Process.
         File sock = new File("SocketDetails.txt");
         processObj = processObj.readSocketDetails(sock, processObj);

         //Thread that will run the ServerSocket and listen for incoming messages continuously
         Thread t = new Process(processObj.port, id);
         t.setName("SERVER");
         processObj.neighbors = processObj.readFile(fin, neighbors, id);
         //Start the Server Socket and listen to incoming messages
         t.start();

         //This will continuously run the command prompt on the main thread
         while(true){
          processObj.displayPrompt();
         }

      }catch(IOException e) {
         e.printStackTrace();
      }
   }
}
