import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.util.Arrays;

public class UDPServer extends UDP {
	static final int SERVER_PORT = 3303;

	public UDPServer() {
		initialize();

		try {
			// open socket
			socket = new DatagramSocket(SERVER_PORT);
			System.out.println("Waiting for a client from Port: " + SERVER_PORT);
			socket.setSoTimeout(TIMEOUT);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	// 3-way connection
	// if SYN input -> send SYN+ACK output
	// and then ACK input -> wait for data from the client
	private boolean connectClient() throws Exception {

		byte[] receivedData = null;
		try {
			receivedData = receivePacket();
		} catch (SocketTimeoutException e) {
			printD("waiting for connection");
			return false;
		}

		System.out.println("Connect a client");
		toAddress = receivingPacket.getAddress();
		toPort = receivingPacket.getPort(); 

		// send an ACK+SYN
		if (verifyCheckSum(receivedData) && getACKSequence(receivedData) == 0 
				&& getFlag(Flag.SYN, receivedData)) {

			// set header
			printD("Send an ACK + SYN to the client");
			setFlag(Flag.ACK, true);
			setFlag(Flag.SYN, true);
			setACKSequence(1);

			
		} else {
			initialize();
			System.out.println("FAIL: invalid value in sending ACK + SYN in connection ");
			return false;
		}


		// send and receive from client an ACK
		receivedData = sendAndReceiveInTime(null);
		if (verifyCheckSum(receivedData) 
				&& getFlag(Flag.ACK, receivedData) 
				&& getACKSequence(receivedData) == 1) {
			isConnected = true;
		} else {
			initialize();
			System.out.println("FAIL: receiving an ACK in connection " 
					+ " checkSum: " + verifyCheckSum(receivedData) 
					+ " ACK?: " + (getACKSequence(receivedData) == 1));
			return false;
		}

		System.out.println("Connected to the client address: " + toAddress 
				+ " port: " + toPort);
		return true;
	}

	// 4-way disconnection
	// if FIN input -> send ACK output
	// and then FIN output -> wait for ACK input
	// if ACK input -> disconnection with the client
	private boolean disconnectClient(byte[] FINpacketData) throws Exception {
		System.out.println("Disconnect the client");

		// send FIN output
		int sendingACK = getACKSequence(FINpacketData) + 1;
		
		// set header
		initialize();
		setACKSequence(sendingACK);
		setFlag(Flag.FIN, true);

		printD("Send FIN");

		// send a FIN packet and wait for ACK input
		byte[] receivedData = sendAndReceiveInTime(null);

		if (verifyCheckSum(receivedData) && getFlag(Flag.ACK, receivedData)
				&& getACKSequence(receivedData) == sendingACK) {
			isConnected = false;
			System.out.println("Disconnected with " + toAddress);
		} else {
			System.out.println("FAIL: invalid checkSum or flag");
			return false;
		}

		initialize();
		return true;
	}

	// receive data from a client
	// wait for a connection
	// receive data
	// disconnect
	public void receive() throws Exception {
		// wait for a connection
		// 3-way handshaking
		while (!connectClient());
			

		byte[] receivedData = null;
		if (isConnected) {
			FileOutputStream fileOutputStream = new FileOutputStream("recv.txt");
			BufferedOutputStream bos = new BufferedOutputStream(fileOutputStream);
			

			long count = 0;
			// receive data from the client
			// using buffer until FIN
			while (true) {
				// receive packet
				try {
					receivedData = receivePacket();
				} catch (SocketTimeoutException e) {
					printD("Receving...");
					continue;
				}
				count++;
				
				// test case 1
				// one packet send by the client will be lost every 10 packets
				if (count % 10 == 0) {
					System.out.println("Test Case 1 occur");
					continue;
				}
				
				// verify checksum
				if (verifyCheckSum(receivedData)) {
					// copy to buffer
					
					byte[] realData = Arrays.copyOfRange(receivedData, DATA_OFFSET, 
							DATA_OFFSET + MAXIMUM_DATA_SIZE);
					
//					System.out.println("RECEIVED: " 
//						+ new String(realData, 0, realData.length));
					
					
					// write to buffer
					bos.write(realData, 0, realData.length);
					count++;

					// send ACK of receive ACK number
					initialize();
					setACKSequence(getACKSequence(receivedData));
					setDataSequence(getDataSequence(receivedData) + 1);
					setFlag(Flag.ACK, true);
					
					makeHeaderAndSend(null);
					
					// if fin flag is true
					if (receivedData != null && getFlag(Flag.FIN, receivedData)) {
						printD("The client sent FIN");
						bos.flush();
						break;
					}
				} else {
					printD("invalid checkSum");
					printD("origin data: " + byteArrayToHex(receivedData));
					printD("mismatched checksum result: " + getCheckSumValue(receivedData));
					printD("size: " + receivedData.length + " " + receivingPacket.getLength());
				}
				
				
			}
		}

		// if FIN
		// disconnect
		// 4-way handshaking
		while (!disconnectClient(receivedData));
	}


	public static void main(String[] args) throws Exception {
		// create UDP server
		UDPServer udpServer = new UDPServer();

		// receive data
		while (true) {
			udpServer.receive();
		}

	}
}
