import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.BitSet;

public class UDP {
	static final boolean debugMode = true;
	static final boolean SEND_RECV_ACK_mode = false;

	static final int MAXIMUM_SEGMENT_SIZE = 1460;
	static final int TIMEOUT = 100; // milli secs

	// size of each field in bytes
	static final int DATA_SEQUENCE_SIZE = 4;
	static final int ACK_SEQUENCE_SIZE = 4;
	static final int FLAGS_SIZE = 1;
	static final int CHECKSUM_SIZE = 2;
	static final int RESERVED_SIZE = 1;
	static final int HEADER_SIZE = DATA_SEQUENCE_SIZE + ACK_SEQUENCE_SIZE + RESERVED_SIZE + FLAGS_SIZE + CHECKSUM_SIZE;
	static final int MAXIMUM_DATA_SIZE = MAXIMUM_SEGMENT_SIZE - HEADER_SIZE;

	// offset of each field in bytes
	static final int DATA_SEQUENCE_OFFSET = 0;
	static final int ACK_SEQUENCE_OFFSET = DATA_SEQUENCE_OFFSET + DATA_SEQUENCE_SIZE;
	static final int RESERVED_OFFSET = ACK_SEQUENCE_OFFSET + ACK_SEQUENCE_SIZE;
	static final int FLAGS_OFFSET = RESERVED_OFFSET + RESERVED_SIZE;
	static final int CHECKSUM_OFFSET = FLAGS_OFFSET + FLAGS_SIZE;
	static final int DATA_OFFSET = CHECKSUM_OFFSET + CHECKSUM_SIZE;

	DatagramSocket socket = null;
	InetAddress toAddress = null;
	int toPort = 0;
	boolean isConnected = false;

	public static enum Flag {
		SYN, ACK, FIN
	}

	// flags
	// including SYN ACK FIN
	// 00000001: SYN / 00000010: ACK / 00000100: FIN / else: currently not used
	// 8 bit == 1 byte
	BitSet flags = null;

	// data sequence field
	// 32 bit == 4 byte
	byte[] dataSequence = null;

	// ACK sequence field
	// 32 bit == 4 byte
	byte[] ACKSequence = null;

	// checksum for header and data
	// same as the IP protocol uses
	// 16 bit == 2 byte
	byte[] checkSum = null;

	// reserved
	byte[] reserved = new byte[1];

	// headerMaker
	ByteArrayOutputStream headerStream = null;

	// receivingPaket
	DatagramPacket receivingPacket;

	// sendingPacket
	DatagramPacket sendingPacket;
	
	// Test Case
	TestCase testCase;

	public UDP() {
		initialize();
	}

	// initialize variables
	void initialize() {
		// initialize to 0
		setDataSequence(0);
		setACKSequence(0);
		if (flags == null) {
			flags = new BitSet();
		} else {
			flags.set(0, FLAGS_SIZE * 8 - 1, false);
		}
		checkSum = ByteBuffer.allocate(CHECKSUM_SIZE).putShort((short) 0).array();
		if (headerStream == null) {
			headerStream = new ByteArrayOutputStream();
		} else {
			headerStream.reset();
		}

	}

	// make header according to field variables
	// and send to the connected address
	void makeHeaderAndSend(byte[] data) throws Exception {
		headerStream.reset();
		headerStream.write(dataSequence);
		headerStream.write(ACKSequence);
		headerStream.write(reserved);
		headerStream.write(flags.toByteArray());
		if (data != null) {
			headerStream.write(data);
		}
		if (headerStream.size() % 2 != 0) {
			headerStream.write(new byte[1]);
		}
		setCheckSum(headerStream.toByteArray());

		// from child
		// test case 2
		if (testCase != null) {
			testCase.case2();
		}
		
		printD("dSeq: " + byteArrayToHex(dataSequence) + " ACKSeq: " + byteArrayToHex(ACKSequence) + " reserved: "
				+ byteArrayToHex(reserved) + " flags: " + byteArrayToHex(flags.toByteArray()) + " checksum: "
				+ byteArrayToHex(checkSum));

		headerStream.reset();
		headerStream.write(dataSequence);
		headerStream.write(ACKSequence);
		headerStream.write(reserved);
		headerStream.write(flags.toByteArray());
		headerStream.write(checkSum);
		if (data != null) {
			headerStream.write(data);
		}

		sendPacket(headerStream.toByteArray());
	}

	byte[] sendAndReceiveInTime(byte[] sendData) throws Exception {
		while(true) {
			try {
				// send packet 
				makeHeaderAndSend(sendData);
				
				// if receive a packet in time
				// return the data
				return receivePacket();
			} catch (SocketTimeoutException e) {
				printD("Timeout....!");
				continue;
			}
		}
	}
	
	void sendPacket(byte[] data) throws Exception {
		sendingPacket = new DatagramPacket(data, data.length, toAddress, toPort);
		printSRA("Send: ACK " + getACKSequence(data));
		socket.send(sendingPacket);
	}

	byte[] receivePacket() throws Exception {
		receivingPacket = new DatagramPacket(new byte[MAXIMUM_SEGMENT_SIZE], MAXIMUM_SEGMENT_SIZE);

		socket.receive(receivingPacket);
		
		printSRA("RECV: ACK " + getACKSequence(receivingPacket.getData()));
		return receivingPacket.getData();
	}

	// verify received CheckSum data for flags / data, ACK sequences / checksum
	boolean verifyCheckSum(byte[] packetData) {
		if ((getCheckSumValue(packetData) & 0xFFFF) == 0x0000) {
			return true;
		}

		return false;
	}

	// byte array to hex string
	String byteArrayToHex(byte[] target) {
		StringBuilder sb = new StringBuilder();
		for (final byte b : target) {
			sb.append(String.format("%02x ", b & 0xFF));
		}

		return sb.toString();
	}

	// set checkSum bits for sending
	void setCheckSum(byte[] target) {
		checkSum = ByteBuffer.allocate(CHECKSUM_SIZE).putChar((char) getCheckSumValue(target)).array();
	}

	// calculate CheckSum Value of target data
	long getCheckSumValue(byte[] target) {
		int len = target.length;
		int i = 0;

		long sum = 0;
		long data;

		// handle all pairs
		while (len > 1) {

			data = (((target[i] << 8) & 0xFF00) | (target[i + 1] & 0xFF));
			sum += data;

			if ((sum & 0xFFFF0000) > 0) {
				sum = sum & 0xFFFF;
				sum += 1;
			}

			i += 2;
			len -= 2;
		}

		// handle remaining byte in odd length target
		if (len > 0) {
			sum += (target[i] << 8 & 0xFF00);

			// 1's complement carry bit correction in 16-bits (detecting sign extension)
			if ((sum & 0xFFFF0000) > 0) {
				sum = sum & 0xFFFF;
				sum += 1;
			}
		}

		// Final 1's complement value correction to 16-bits
		sum = ~sum;
		sum = sum & 0xFFFF;
		return sum;
	}

	// set flags according to input type
	void setFlag(Flag type, boolean value) {
		// value == 0 -> false
		// value == 1 -> true

		switch (type) {
		case SYN:
			// set SYN flag
			if (value == true) {
				flags.set(0);
			} else {
				// implement
				flags.set(0, false);
			}
			break;
		case ACK:
			// set ACK flag
			if (value == true) {
				flags.set(1);
			} else {
				// implement
				flags.set(1, false);
			}
			break;
		case FIN:
			// set FIN flag
			if (value == true) {
				flags.set(2);
			} else {
				// implement
				flags.set(3, false);
			}
			break;
		}
	}

	// get a flag of header
	boolean getFlag(Flag type, byte[] packetData) {
		byte[] flagsField = Arrays.copyOfRange(packetData, FLAGS_OFFSET, FLAGS_OFFSET + FLAGS_SIZE);

		BitSet flagsBitSet = BitSet.valueOf(flagsField);

		boolean result = false;

		switch (type) {
		case SYN:
			result = flagsBitSet.get(0);
			break;
		case ACK:
			result = flagsBitSet.get(1);
			break;
		case FIN:
			result = flagsBitSet.get(2);
			break;
		}

		return result;
	}

	// set data sequence number
	void setDataSequence(int num) {
		dataSequence = ByteBuffer.allocate(DATA_SEQUENCE_SIZE).putInt(num).array();
	}

	// set ACK sequence number
	void setACKSequence(int num) {
		ACKSequence = ByteBuffer.allocate(ACK_SEQUENCE_SIZE).putInt(num).array();
	}

	// get Data sequence number
	int getDataSequence(byte[] packetData) {
		byte[] dataSeq = Arrays.copyOfRange(packetData, DATA_SEQUENCE_OFFSET,
				DATA_SEQUENCE_OFFSET + DATA_SEQUENCE_SIZE);

		return ByteBuffer.wrap(dataSeq).getInt();
	}

	// get ACK sequence number
	int getACKSequence(byte[] packetData) {
		byte[] ACKSeq = Arrays.copyOfRange(packetData, ACK_SEQUENCE_OFFSET, ACK_SEQUENCE_OFFSET + ACK_SEQUENCE_SIZE);

		return ByteBuffer.wrap(ACKSeq).getInt();
	}

	// if debugMode == true, print debug information
	static void printD(String s) {
		if (debugMode)
			System.out.println(s);
	}

	// if SEND_RECV_ACK == true, print sending receiving ack packet information
	static void printSRA(String s) {
		if (SEND_RECV_ACK_mode)
			System.out.println(s);
	}

	
	// set interface
	void setTestCaseInteface(TestCase tc) {
		testCase = tc;
	}
	
	interface TestCase {
		abstract void case2();
	}
}
