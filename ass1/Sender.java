import java.io.*;
import java.math.*;
import java.net.*;
import java.util.*;

public class Sender 
{
	private InetAddress receiver_host_ip = null;
	private int receiver_port = 0;
	private String file_name = null;

	private File file = null;
	private int MWS = 0;
	private int MSS = 0;
	private int timeout = 0;
	
	private Float pdrop = 0.0f;
	private long seed = 0;
	private Random random = null;
	
	private StringBuffer log = null;
	private long start_time = 0;
	
	private int NDSS = 0;
	private int NPD = 0;
	private int NRS = 0;
	private int NDA = 0;
	
	private byte[] payload_buffer = null;
	
	private int max_sequence_number = 0;
	
	private DatagramSocket SenderSocket = null;
	
	// S means S->R, Send message from Sender.
	private int client_isn = 0;
	
	private int SSequence_number = 0;
	private int SAcknowledgment_number = 0;
	private int SSYN = 0;
	private int SACK = 0;
	private int SFIN = 0;
	
	// R means R->S, Receive message from Receiver.
	private int server_isn = 0;
	
	private int RSequence_number = 0;
	private int RAcknowledgment_number = 0;
	private int RSYN = 0;
	private int RACK = 0;
	private int RFIN = 0;
	
	private String state = null;
	
	public Sender(String[] args)
	{
		try
		{
			receiver_host_ip = InetAddress.getByName(args[0].toString());
			receiver_port = Integer.parseInt(args[1]);	
			file_name = args[2].toString();
			file = new File(file_name);
			
			MWS = Integer.parseInt(args[3]);
			MSS = Integer.parseInt(args[4]);
			timeout = Integer.parseInt(args[5]);
			
			pdrop = Float.parseFloat(args[6]);
			seed = Long.parseLong(args[7]);
			random = new Random(seed);
			
			log = new StringBuffer();
			start_time = System.nanoTime();
			
			log.append(new String().format("<snd/rcv/drop> <time> <type of packet> <seq-number> <number-ofbytes> <ack-number>%n"));
			
			SenderSocket = new DatagramSocket();
			state = "GO";
			
			System.out.println("Sender estabilshed.");

		}
		catch (Exception error)
		{
			System.out.println("Error in initiate a sender.");
		}
	}
	
	// add header of STP to the datagrampacket. 
	// Sequence number, Acknowledgment number, SYN, ACK, FIN, payload. As a head.
	// MWS, MSS.
	// 19
	
	// STP head, from SAASF to string.
	public String STPhead(int Sn, int An, int Syn, int Ack, int Fin)
	{
		return String.format("%08d%08d%01d%01d%01d", Sn, An, Syn, Ack, Fin);
	}
	
	// From head to SASAF.
	public int[] STPhead(String head)
	{
		int[] SASAF = {Integer.parseInt(head.substring(0, 8)), Integer.parseInt(head.substring(8, 16)), Integer.parseInt(head.substring(16, 17)), Integer.parseInt(head.substring(17, 18)), Integer.parseInt(head.substring(18, 19))};
		return SASAF;
	}
	
	// add head to datagramPacket.
	public DatagramPacket STPpacket(String head, String data)
	{
		byte[] sendData;
		
		if (data == null || "".equals(data))
		{
			sendData = head.getBytes();
			
		}
		else
		{
			sendData = (head + data).getBytes();
		}
		DatagramPacket STPpacket = new DatagramPacket(sendData, sendData.length, receiver_host_ip, receiver_port);
		return STPpacket;
	}
	
	// From datagramPacket to head + data.
	public String[] STPpacket(DatagramPacket packet) throws Exception
	{
		byte[] buf = packet.getData();

		String line2 = new String(buf);
		
		String[] head_payload = {null, null};
		
		head_payload[0] = line2.substring(0, 19);
		head_payload[1] = line2.substring(19, line2.length());

		return head_payload;
	}
	
	public void send_packet(DatagramPacket STPpacket) throws Exception
	{
		BigDecimal m_time = BigDecimal.valueOf(System.nanoTime()-start_time,6);
		String log_snd = null;
		
		if (random.nextFloat() < pdrop && "ESTABLISHED".equals(state))
		{
			// log NPD
			NPD ++;
			// <snd/rcv/drop> <time> <type of packet> <seq-number> <number-ofbytes> <ack-number>
			log_snd = new String().format("drop % 9.2f  D % 8d % 4d % 8d", m_time, STPhead(STPpacket(STPpacket)[0])[0], STPpacket(STPpacket)[1].length(), STPhead(STPpacket(STPpacket)[0])[1]);
			log.append(log_snd+"\n");
			
			System.out.println(log_snd);
			return;
		}
		else
		{
			SenderSocket.send(STPpacket);
			
			// STPhead(STPpacket(STPpacket)[0])[2]
			
			if (STPhead(STPpacket(STPpacket)[0])[2] == 1)
			{
				// SYN = 1
				log_snd = new String().format("snd  % 9.2f  S % 8d    0 % 8d", m_time, STPhead(STPpacket(STPpacket)[0])[0], STPhead(STPpacket(STPpacket)[0])[1]);
				
			}
			else if (STPhead(STPpacket(STPpacket)[0])[3] == 1)
			{
				// ACK = 1
				log_snd = new String().format("snd  % 9.2f  A % 8d    0 % 8d", m_time, STPhead(STPpacket(STPpacket)[0])[0], STPhead(STPpacket(STPpacket)[0])[1]);
				
			}
			else if (STPhead(STPpacket(STPpacket)[0])[4] == 1)
			{
				// FIN = 1
				log_snd = new String().format("snd  % 9.2f  F % 8d    0 % 8d", m_time, STPhead(STPpacket(STPpacket)[0])[0], STPhead(STPpacket(STPpacket)[0])[1]);
				
			}
			else
			{
				// it is DATA
				log_snd = new String().format("snd  % 9.2f  D % 8d % 4d % 8d", m_time, STPhead(STPpacket(STPpacket)[0])[0], STPpacket(STPpacket)[1].length(), STPhead(STPpacket(STPpacket)[0])[1]);
				
			}
			log.append(log_snd+"\n");
			
			System.out.println(log_snd);
		}
	}
	
	public DatagramPacket receive_packet(int timeout) throws Exception
	{
		byte[] receiveData = new byte[1024];
		DatagramPacket receive_packet = new DatagramPacket(receiveData, receiveData.length);
		
		// waiting. try catch is out of this function.
		SenderSocket.setSoTimeout(timeout);
		SenderSocket.receive(receive_packet);
		
		// if not time out.
		String receive_packet_head = STPpacket(receive_packet)[0];
		
		RSequence_number = STPhead(receive_packet_head)[0];
		RAcknowledgment_number = STPhead(receive_packet_head)[1];
		RSYN = STPhead(receive_packet_head)[2];
		RACK = STPhead(receive_packet_head)[3];
		RFIN = STPhead(receive_packet_head)[4];
		
		// log
		BigDecimal m_time = BigDecimal.valueOf(System.nanoTime()-start_time,6);
		String log_rcv = null;
		
		if (RSYN == 1 && RACK == 1)
		{
			// SYN&ACK = 1
			log_rcv = new String().format("rcv  % 9.2f SA % 8d    0 % 8d", m_time, RSequence_number, RAcknowledgment_number);
		}
		else if (RACK == 1)
		{
			// ACK = 1
			log_rcv = new String().format("rcv  % 9.2f  A % 8d    0 % 8d", m_time, RSequence_number, RAcknowledgment_number);
		}
		else if (RFIN == 1)
		{
			// FIN = 1
			log_rcv = new String().format("rcv  % 9.2f  F % 8d    0 % 8d", m_time, RSequence_number, RAcknowledgment_number);
		}
		log.append(log_rcv+"\n");
		System.out.println(log_rcv);
		
		return receive_packet;
	}
	
	public void STP_connect(int isn) throws Exception
	{
		client_isn = isn;
		int retry_times = 0;
		
		while (!"ESTABLISHED".equals(state) && retry_times < 5 && !"CLOSED".equals(state))
		{
			switch (state)
			{
			case "GO":
				// 3.5.6 -TCP connection management, step 1.
				SSYN = 1;
				SSequence_number = client_isn;
				// 3.5.6 -TCP connection management
				
				String SYN_head = STPhead(SSequence_number, SAcknowledgment_number, SSYN, SACK, SFIN);
				DatagramPacket SYN_packet = STPpacket(SYN_head, null);
				send_packet(SYN_packet);
				
				state = "SYN_SENT";
				System.out.println("SYN sent, waiting for synack");
				break;
				
			case "SYN_SENT":
				try
				{
					// waiting here
					DatagramPacket SYNACK_packet = receive_packet(timeout);
					// waiting here
					
					
					server_isn = RSequence_number;
					
					if (RSYN == 1 && RAcknowledgment_number == SSequence_number + 1)
					{
						// 3.5.6 -TCP connection management, step 3.
						SSYN = 0;
						SSequence_number ++;
						SAcknowledgment_number = server_isn + 1;
						SACK = 1;
						// 3.5.6 -TCP connection management, step 3.
						
						String ACK_head = STPhead(SSequence_number, SAcknowledgment_number, SSYN, SACK, SFIN);
						DatagramPacket ACK_packet = STPpacket(ACK_head, null);
						send_packet(ACK_packet);
						
						
						state = "ESTABLISHED";
						System.out.println("connection established");
						
						// add data to buffer.
						// give the data max seq number
						
						ByteArrayOutputStream bos = new ByteArrayOutputStream((int) file.length());
				        BufferedInputStream in = null;  
				        try 
				        {
				            in = new BufferedInputStream(new FileInputStream(file));  
				            int buf_size = 1024;  
				            byte[] buffer = new byte[buf_size];  
				            int len = 0;
				            
				            while (-1 != (len = in.read(buffer, 0, buf_size))) 
				            {  
				                bos.write(buffer, 0, len);  
				            }
				            //System.out.println(bos.size());
				            payload_buffer = bos.toByteArray();
				        }
				        catch (IOException e) 
				        {
				            e.printStackTrace();  
				            throw e;  
				        }
				        finally 
				        {
				            try 
				            {
				                in.close();  
				            } 
				            catch (IOException e) 
				            {
				                e.printStackTrace();  
				            }  
				            bos.close();  
				        }
					}
					else
					{
						state = "GO";
						retry_times ++;
						System.out.println("not receive correct synack, set state to go, retry_times++ "+(retry_times));
					}
				}
				catch (java.net.SocketTimeoutException ex)
				{
					retry_times ++;
					System.out.println("connection time out, set state to go, retry_times++ "+(retry_times));
					state = "GO";
				}
				break;
			}
		}
		
		if (retry_times > 5)
		{
			System.out.println("retry too many times "+(retry_times));
			state = "CLOSED";
		}
	}
	
	public void STP_transimit() throws Exception
	{
		if (!"ESTABLISHED".equals(state))
		{
			System.out.println("not ESTABLISHED, cannot transimit");
			return;
		}
		
		//------------------------------------------------------------------
		max_sequence_number = SSequence_number + payload_buffer.length;
		int ns = SSequence_number;
		int sb = SSequence_number;
		SACK = 0;
		
		long timer = 0;
		
		int LRACK = 0;
		int DRACK = 0;

		// System.out.println(max_sequence_number+" "+payload_buffer.length%MSS);
		
		
		while (sb != max_sequence_number)
		{
//			System.out.println("NS: "+ns+" SB: "+sb+" MWS: "+MWS);
			if (ns - sb < MWS && ns < max_sequence_number)
			{
				String tran_head = STPhead(ns, SAcknowledgment_number, 0, 0, 0);
				String payload = null;
				
				if ( ns+MSS < max_sequence_number)
				{
					payload = new String(payload_buffer, ns-client_isn-1, MSS);
				}
				else
				{
					payload = new String(payload_buffer, ns-client_isn-1, payload_buffer.length%MSS);
					
				}
				DatagramPacket tran_packet = STPpacket(tran_head, payload);
				
				// log the NDSS;
				NDSS ++;
				
				send_packet(tran_packet);
				// timestamp
				if (timer == 0)
				{
//					Timestamp d = new Timestamp(System.nanoTime()); 
					timer = System.currentTimeMillis();
//					System.out.println(timer);
				}
				
				SSequence_number += payload.length();
				ns += payload.length();
//				System.out.println(SSequence_number==ns);
				continue;
			}
			
			if (System.currentTimeMillis()-timer >= timeout)
			{
				// log NRS
				NRS ++;
				// resend
				String tran_head = STPhead(RAcknowledgment_number, SAcknowledgment_number, 0, 0, 0);
				String payload = null;
				if ( RAcknowledgment_number+MSS < max_sequence_number)
				{
					payload = new String(payload_buffer, RAcknowledgment_number-client_isn-1, MSS);
				}
				else
				{
					payload = new String(payload_buffer, RAcknowledgment_number-client_isn-1, payload_buffer.length%MSS);
				}
				DatagramPacket tran_packet = STPpacket(tran_head, payload);
				send_packet(tran_packet);
				
				// new timestamp
				timer = System.currentTimeMillis();
				continue;
			}
			
			if (sb != max_sequence_number)
			{
				try
				{
					// waiting
					DatagramPacket dataACK_packet = receive_packet(1);
					
					// if receive packet.
					if (RAcknowledgment_number > sb)
					{
						sb = RAcknowledgment_number;
						if (sb != max_sequence_number)
						{
							timer = System.currentTimeMillis();
							// timestamp
						}
					}
					else
					{
						// log NDA
						NDA ++;
//						System.out.println(DRACK+" "+LRACK);
						DRACK ++;
						
						if (LRACK != RAcknowledgment_number)
						{
							DRACK = 1;
							LRACK = RAcknowledgment_number;
						}
						
						
						
						if (DRACK == 3)
						{
							// log NRS
							NRS ++;
							// resend
							
							String tran_head = STPhead(RAcknowledgment_number, SAcknowledgment_number, 0, 0, 0);
							String payload = null;
							if ( RAcknowledgment_number+MSS < max_sequence_number)
							{
								payload = new String(payload_buffer, RAcknowledgment_number-client_isn-1, MSS, "UTF-8");
							}
							else
							{
								payload = new String(payload_buffer, RAcknowledgment_number-client_isn-1, payload_buffer.length%MSS, "UTF-8");
								
							}
							DatagramPacket tran_packet = STPpacket(tran_head, payload);
							send_packet(tran_packet);
							// new timestamp
							timer = System.currentTimeMillis();
						}
					}
				}
				catch (java.net.SocketTimeoutException ex)
				{
					continue;
				}
			}
		}
		
		System.out.println("data transmit complete. set state to FINISH");
		state = "FINISH";
		
	}
	
	public void STP_finish() throws Exception
	{
		if (!"FINISH".equals(state))
		{
			System.out.println("not FINISH, cannot FINISH");
			return;
		}
		//------------------------------
		while (!"CLOSED".equals(state))
		{
			switch (state)
			{
			case "FINISH":
				// SEND fin
				String head = STPhead(RAcknowledgment_number, RSequence_number, 0, 0, 1);
				send_packet(STPpacket(head, null));
				state = "FIN_WAIT_1";
				break;
			case "FIN_WAIT_1":
				// wait for ack
				try 
				{
					DatagramPacket SYNACK_packet = receive_packet(timeout);
					if (RACK == 1)
					{
						state = "FIN_WAIT_2";
					}
					else
					{
						state = "FINISH";
					}
				}
				catch (java.net.SocketTimeoutException ex)
				{
					state = "FINISH";
				}
				break;
			case "FIN_WAIT_2":
				// wait for fin
				try 
				{
					DatagramPacket SYNACK_packet = receive_packet(timeout);
					if (RFIN == 1)
					{
						String head1 = STPhead(RAcknowledgment_number, RSequence_number, 0, 1, 0);
						send_packet(STPpacket(head1, null));
						state = "CLOSED";
					}
					else
					{
						state = "FIN_WAIT_2";
					}
				}
				catch (java.net.SocketTimeoutException ex)
				{
					String head1 = STPhead(RAcknowledgment_number, RSequence_number, 0, 1, 0);
					send_packet(STPpacket(head1, null));
					state = "CLOSED";
				}
				break;
			}
		}
		
		// write log
//		Amount of Data Transferred (in bytes)
//		Number of Data Segments Sent (excluding retransmissions)
//		Number of Packets Dropped (by the PLD module)
//		Number of Retransmitted Segments
//		Number of Duplicate Acknowledgements received
		BigDecimal end_time = BigDecimal.valueOf(System.nanoTime()-start_time,6);
		String log_end = null;
		log_end = new String("Amount of Data Transferred: "+payload_buffer.length+"\n");
		log_end += new String("Number of Data Segments Sent: "+NDSS+"\n");
		log_end += new String("Number of Packets Dropped: "+NPD+"\n");
		log_end += new String("Number of Retransmitted Segments: "+NRS+"\n");
		log_end += new String("Number of Duplicate Acknowledgement received: "+NDA+"\n");
		
		log.append(log_end);
		
		File f = new File("Sender_log.txt");
		FileWriter fw = new FileWriter(f.getAbsoluteFile());
		BufferedWriter bw = new BufferedWriter(fw);
		bw.write(log.toString());
		bw.close();
		
	}
	
	
//	args[] should be 8, which has:
//	1. receiver_host_ip: the IP address of the host machine on which the Receiver is running.
//	2. receiver_port: the port number on which Receiver is expecting to receive packets from
//	the sender.
//	3. file.txt: the name of the text file that has to be transferred from sender to receiver using
//	your reliable transport protocol.
//	4. MWS: the maximum window size used by your STP protocol in bytes.
//	5. MSS: Maximum Segment Size which is the maximum amount of data (in bytes) carried in
//	each STP segment.
//	6. timeout: the value of timeout in milliseconds
	// NOT USED FOR VERSION 1.0
//	7. pdrop: the probability that a STP data segment which is ready to be transmitted will be
//	dropped. This value must be between 0 and 1. For example if pdrop = 0.5, it means that
//	50% of the transmitted packets are dropped by the PLD.
//	8. seed: The seed for your random number generator. The use of seed will be explained in
//	Section 4.5.2 of the specification.
//	127.0.0.1 1200 file 30 60 300
	public static void main(String[] args) throws Exception
	{
		System.out.println("Hello world.");
		if (args.length != 8)
		{
			System.out.println("Required arguments should be 6. You must foget something.");
			return;
		}
		
		/*Task task = new Task();
		FutureTask<String> futureTask = new FutureTask<String>(task);
		Thread thread = new Thread(futureTask);
		thread.start();*/
		
		Sender aa = new Sender(args);
		
		aa.STP_connect(500);
		
		aa.STP_transimit();
		
		aa.STP_finish();

		
		aa.SenderSocket.close();
		
	}
}