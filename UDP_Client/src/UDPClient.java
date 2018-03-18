import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class UDPClient extends UDP {
	static final int CLIENT_PORT = 3333;

	long sendingCount = 0;

	public UDPClient() {
		try {
			// open client socket
			socket = new DatagramSocket();
			socket.setSoTimeout(TIMEOUT);

			initialize();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// connect to server
	// 3-way handshaking connection
	public void connectServer(String serverName, int port) throws Exception {
		System.out.println("Connect to " + serverName + ": " + port);
		// set server address and port
		toAddress = InetAddress.getByName(serverName);
		toPort = port;

		printD("send SYN packet to the server ACK: " + getACKSequence(ACKSequence));
		// set SYN packet
		initialize();
		setFlag(Flag.SYN, true);

		printD("wait for server's ACK + SYN");
		// send SYN packet and wait server's ACK + SYN
		byte[] receivedData = sendAndReceiveInTime(null);

		printD("received ACK + SYN from the server");
		// check ACK + SYN
		if (verifyCheckSum(receivedData) && getFlag(Flag.ACK, receivedData)
				&& getFlag(Flag.SYN, receivedData)
				&& getACKSequence(receivedData) == 1) {

			// send ACK to server
			initialize();
			setFlag(Flag.ACK, true);
			setACKSequence(getACKSequence(receivedData));

			printD("Send ACK to the server");
			makeHeaderAndSend(null);

		} else {
			System.out.println("FAIL: while connecting to server");
			isConnected = false;
		}

		System.out.println("Connected");
		isConnected = true;
	}

	// disconnect
	// 4-way handshaking
	// just send FIN packet
	private void disconnectServer() throws Exception {
		System.out.println("Disconnect the server");

		// set FIN packet to the server
		int sendingACK = getACKSequence(ACKSequence) + 1;
		initialize();
		setFlag(Flag.FIN, true);
		setACKSequence(sendingACK);

		// send FIN packet and wait for ACK
		while (true) {
			byte[] receivedData = sendAndReceiveInTime(null);
			if (verifyCheckSum(receivedData) && getACKSequence(receivedData) == sendingACK
					&& getFlag(Flag.ACK, receivedData)) {
				// successfully received ACK for FIN packet

				while (true) {
					// wait for FIN packet from the server
					receivedData = receivePacket();

					if (verifyCheckSum(receivedData) 
							&& getACKSequence(receivedData) == sendingACK + 1
							&& getFlag(Flag.FIN, receivedData)) {
						// successfully received FIN from the server

						// send an ACK to the server for the FIN
						initialize();
						setFlag(Flag.ACK, true);
						setACKSequence(getACKSequence(receivedData));

						makeHeaderAndSend(null);

						System.out.println("Disconnected");
						break;
					} else {
						System.out.println("FAIL: not receivd FIN from the server");
						continue;
					}
				}

				break;
			} else {
				System.out.println("FAIL: not received ACK for FIN");
				continue;
			}
		}

		// close the socket
		socket.close();

	}

	// send string to server some keyboard inputs
	public void sendText() throws Exception {
		int sendingACK = 20;
		BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
		String sentence = inFromUser.readLine();
		byte[] sendData = new byte[MAXIMUM_DATA_SIZE];

		while (!sentence.equals("end")) {

			System.out.println("sending to server: " + sentence);
			sendData = sentence.getBytes();

			// set packet
			initialize();
			setFlag(Flag.ACK, true);
			setACKSequence(sendingACK); // test value

			byte[] receivedData = sendAndReceiveInTime(sendData);
			if (verifyCheckSum(receivedData) && getACKSequence(receivedData) == sendingACK
					&& getFlag(Flag.ACK, receivedData)) {
				sendingACK++;

				sentence = inFromUser.readLine();
			}
		}
		disconnectServer();
	}

	// send file to server
	public void sendFile(String url) throws Exception {
		File sendingFile = new File(url);
		double requiredPackets = Math.ceil((int) sendingFile.length() / MAXIMUM_DATA_SIZE);

		BufferedInputStream fileInputStream 
			= new BufferedInputStream(new FileInputStream(sendingFile));

		int ACKnum = 1000;
		long startTime = System.currentTimeMillis();
		DateFormat formatter = new SimpleDateFormat("HH:mm:ss:SSS");
		System.out.println("Send a file: " + url + "----- start: " 
				+ formatter.format(new Date(startTime)));
		for (double i = 0; i < requiredPackets + 1; i++) {
			byte[] sendingData = new byte[MAXIMUM_DATA_SIZE];
			fileInputStream.read(sendingData, 0, sendingData.length);
			System.out.println("Packet: " + (i + 1));

			// set packet
			initialize();
			setFlag(Flag.ACK, true);
			setACKSequence(ACKnum);
			setDataSequence((int) i + 1);

			byte[] receivedData = null;
			while (true) {
				// send the packet and wait for ACK

				// test case 2
				// one packet send by the client will be corrupted 
				// every 20 packets except every 200 packets
				// the corrupted packet has its wrong checksum
				setTestCaseInteface(new TestCase() {
					@Override
					public void case2() {
						// wrong checksum value
						// in this case, just put 0 in check sum field
						if (sendingCount % 20 == 0 && sendingCount % 200 != 0) {
							System.out.println("Test Case 2 occur");
							setCheckSum(new byte[2]);
						}
						sendingCount++;
					}
				});
				receivedData = sendAndReceiveInTime(sendingData);

				// check header of received packet header
				if (verifyCheckSum(receivedData) && getFlag(Flag.ACK, receivedData)
						&& getACKSequence(receivedData) == ACKnum) {
					// successfully receive an ACK from the server
					ACKnum++;
					break;
				}
			}

			if (fileInputStream.available() <= 0) {
				System.out.println("Total packet: " + (i + 1) 
						+ " sending count: " + sendingCount);
				
				disconnectServer();
				break;
			}
		}

		System.out.println("Send finished: " 
				+ formatter.format(new Date(System.currentTimeMillis() - startTime)));
	}

	public static void main(String[] args) throws Exception {
		UDPClient UDPclient = new UDPClient();

		UDPclient.connectServer("localhost", 3303);

		// UDPclient.sendText();
		UDPclient.sendFile("udp.txt");
	}

}