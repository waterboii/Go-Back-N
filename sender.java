import java.io.*;
import java.util.*;
import java.net.*;

import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class sender{
	private static final int winSize = 10;
	private static final int maxSize = 500;

	private static LinkedList<String> packets = new LinkedList<String>();
	private static Timer packetTimer = null;
	private static int pivot = 0;
	private static int nextSeq = 0;
	private static BufferedWriter seqnum_log;
	private static BufferedWriter ack_log;

	private static int counter = 0;
	private static int checkAck;
	private static boolean duplicate = false;
	private static packet sendPacket;
	private static packet receivePacket;

	private static byte[] content = new byte[65535];
	private static byte[] acks = new byte[65535];

	private static DatagramSocket sock = null;
	private static DatagramPacket sendData;
	private static DatagramPacket receiveData;
	
	private static InetAddress emulatorHost = null;
	private static int emulatorPort = 0;
	private static int senderPort = 0;
	private static String fileName;
	
	//use actionlistener to handle time out
	private static void setTimer(){
		ActionListener timeOut = new ActionListener(){

			public void actionPerformed(ActionEvent event){
				int counter = pivot;
				while ( counter < nextSeq && counter < packets.size()){
					try{
						sendPacket = packet.createPacket(counter % 32, packets.get(counter));
						content = sendPacket.getUDPdata();
						sendData = new DatagramPacket(content, content.length, emulatorHost, emulatorPort);
					}
					catch(Exception e){
						System.exit(1);
					}
					// re-send successful, write it into sqenum.log
					try{
						sock.send(sendData);
						seqnum_log.write(Integer.toString(counter % 32) + '\n');
						counter++;
					}
					catch(IOException e){
						System.exit(1);
					}
				}//loop
			}// actionPerformed
		};//timeout
		// create timer
		packetTimer = new Timer(2000, timeOut);
	}
	
	private static void sendDataPackets(){
	//check window
		while((nextSeq < (pivot + winSize)) && (nextSeq < packets.size())){
			try{					
				sendPacket = packet.createPacket(nextSeq % 32, packets.get(nextSeq));
			}catch(Exception e){
				System.exit(1);
			}
			try{
				content = sendPacket.getUDPdata();
				sendData = new DatagramPacket(content, content.length, emulatorHost, emulatorPort);
				sock.send(sendData);
			//packet send, write it into seqnum_log.
				seqnum_log.write(Integer.toString(nextSeq % 32) + '\n');
			}catch(IOException e){
				System.exit(1);
			}
			if(pivot == nextSeq){
				packetTimer.start();
			}
			nextSeq++;
		}
	}

	private static void sendEOTPackets(){
		if(pivot == packets.size()){
			try{
				sendPacket = packet.createEOT(nextSeq % 32);
			}catch(Exception e){
				System.exit(1);
			}
			try{
				content = sendPacket.getUDPdata();
				sendData = new DatagramPacket(content, content.length, emulatorHost, emulatorPort);
				sock.send(sendData);
			}catch(IOException e){
				System.exit(1);
			}
		}
	}

	private static void receiveACKPackets(){
		// ack received, write it into ack_log
		try{
			ack_log.write(Integer.toString(receivePacket.getSeqNum()) + '\n');
		}catch(IOException e){
			System.exit(1);
		}
		//set duplicate flag
		if(duplicate == false){
			pivot = 1;
			duplicate = true;
		}
		// if received packet is duplicate, ignore it. if not,
		else if(receivePacket.getSeqNum() != checkAck){
			pivot = receivePacket.getSeqNum() + (counter * 32) + 1;
			if(receivePacket.getSeqNum() == 31){
				counter++;
			}
		}
		checkAck = receivePacket.getSeqNum();
		if(pivot == nextSeq){//no transmitted but not acked packets
			packetTimer.stop();
		}
		else{ 
			packetTimer.start(); 
		}
	}
	//read and process input file
	private static void processFile(String fileName){
		BufferedReader infile = null;;
		try{
			FileReader inFile = new FileReader(fileName);
			infile =  new BufferedReader(inFile);
		}catch(FileNotFoundException e){
				System.exit(1);
		}
		
		// accumulate 500 bytes and store in packet
		try{
		String rest = "";
		String line = "";
			while(infile.ready()){
				line = rest + infile.readLine();
				while(line.length() > maxSize - 1){
					packets.add(line.substring(0, maxSize));
					if(line.length() == maxSize){
						rest = "";
					}
					else{
						line = line.substring(maxSize);

					}
				}
				rest = line;
				//if this is the last line, add it to packets even it's not full
				if(!infile.ready()){	
					packets.add(rest);
				}
				// readline ignores \n, add it here
				rest = rest + '\n';
			}//loop
			// close the infile
			infile.close();
			}catch (IOException e){
			System.exit(1);
		}
	}//processFile

	public static void main(String argv[]){
	    //initialize
		// check if input correctly typed
		if(argv.length != 4){
			System.err.println("Error: Incorrect number of arguments");
			System.exit(1);
		}
		try{
		emulatorHost = InetAddress.getByName(argv[0]);
		}catch(UnknownHostException e){
			System.exit(1);
		}
		emulatorPort = Integer.parseInt(argv[1]);
		senderPort = Integer.parseInt(argv[2]);
		fileName = argv[3];
		// read in and process file
		processFile(fileName);
		// set up timer
		setTimer();

		try{
		seqnum_log = new BufferedWriter(new FileWriter("seqnum.log"));
		ack_log = new BufferedWriter(new FileWriter("ack.log"));
		}
		catch(IOException e){
			System.exit(1);
		}
		
		// create an UDP socket
		try{
		sock = new DatagramSocket(senderPort);
		}
		catch(SocketException e){
			System.exit(1);
		}
		try{
		for(;;){
			// sending side
			sendDataPackets();
			sendEOTPackets();
			// receiving side
			receiveData = new DatagramPacket(acks, acks.length);
			sock.receive(receiveData);
			receivePacket = packet.parseUDPdata(acks); // packet received
			//packet type is ack
			if(receivePacket.getType() == 0){
				receiveACKPackets();
			}
			//eot, clean up
			else if(receivePacket.getType() == 2){
				seqnum_log.close();
				ack_log.close();
				sock.close();
				break;
			}
		}//loop
		}catch(Exception e){
			System.exit(1);
		} 
	}
}