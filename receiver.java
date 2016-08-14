import java.io.*;
import java.net.*;

public class receiver{
	private static InetAddress emulatorHost = null;
	private static int emulatorPort = 0;
	private static int receiverPort = 0;
	private static String outFile;
	
	private static BufferedWriter arrival_log = null;
	private static BufferedWriter outputfile = null;

	private static int expectedSeqnum = 0;
	private static boolean duplicate = false;
	private static packet receivePacket;
	private static packet sendPacket;

	private static byte[] content = new byte[65535];
	private static byte[] acks = new byte[65535];

	private static DatagramSocket sock = null;
	private static DatagramPacket sendData;
	private static DatagramPacket receiveData;
	
	public static void main(String argv[]){
		// check arguments
		if(argv.length != 4){
			System.err.println("Error: Incorrect number of arguments");
			System.exit(1);
		}
		// read input
		try{
			emulatorHost = InetAddress.getByName(argv[0]);
		}catch(UnknownHostException e){
			System.err.println("ERROR: " + e.getMessage());
		}
		emulatorPort = Integer.parseInt(argv[1]);
		receiverPort = Integer.parseInt(argv[2]);
		outFile = argv[3];
		try{
			arrival_log = new BufferedWriter(new FileWriter("arrival.log"));
			outputfile = new BufferedWriter(new FileWriter(outFile));
		}catch (IOException e){
			System.exit(1);
		}
		// create UDP socket
		try{
			sock = new DatagramSocket(receiverPort);
		}catch(SocketException e){
			System.exit(1);
		}
		try{
			for(;;){
				receiveData = new DatagramPacket(content, content.length);
				sock.receive(receiveData);
				receivePacket = packet.parseUDPdata(content);
				// type is data
				if(receivePacket.getType() == 1){
					// received data, write it in arrival.log
					arrival_log.write(Integer.toString(receivePacket.getSeqNum()) + '\n');
					if(receivePacket.getSeqNum() == expectedSeqnum % 32){
						//write data to ouput and reply ack with seqnum
						outputfile.write(new String(receivePacket.getData()));
						sendPacket = packet.createACK(expectedSeqnum % 32);
						acks = sendPacket.getUDPdata();
						sendData = new DatagramPacket(acks, acks.length, emulatorHost, emulatorPort);
						sock.send(sendData);
						duplicate = true;
						expectedSeqnum++;
					} 
					else if((duplicate == false) && (receivePacket.getSeqNum() != expectedSeqnum % 32)){
						continue;
					}
					// if seqnums not in order
					else{
					// send ack with highest segnum
						sendPacket = packet.createACK(expectedSeqnum % 32 - 1);
						acks = sendPacket.getUDPdata();
						sendData = new DatagramPacket(acks, acks.length, emulatorHost, emulatorPort);
						sock.send(sendData);
					}
				}
				// type is eot
				else if(receivePacket.getType() == 2){
				//send eot packet and clean up
					sendPacket = packet.createEOT(receivePacket.getSeqNum());
					acks = sendPacket.getUDPdata();
					sendData = new DatagramPacket(acks, acks.length, emulatorHost, emulatorPort);
					sock.send(sendData);
	
					outputfile.close();
					arrival_log.close();
					sock.close();
					break;
				}//else if 
			}//while
		} catch(Exception e){
			System.exit(1);
		} 
	}
}