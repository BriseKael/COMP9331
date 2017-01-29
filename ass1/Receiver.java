import java.io.*;
import java.math.*;
import java.net.*;
import java.util.*;


class PacketBuffer
{
	public int index = 0;
	public String data = null;
}

public class Receiver 
{
	
	private InetAddress sender_host_ip = null;
	private int sender_port = 0;
	
	private int receiver_port = 0;
	private String file_name = null;
	private String file_path = null;
	
	private StringBuffer log = null;
	private long start_time = 0;
	private long run_time = 0;
	
	private int NDSR = 0;
	private int N = 0;
	
	private StringBuffer payload_buf = null;
	private Vector buffer = null;
	private Vector buffer_key = null;
	
	private int head_length = 0;
	
	private DatagramSocket ReceiverSocket = null;
	
	// S means R->S, Send message from Receiver.
	private int server_isn = 0;
	
	private int SSequence_number = 0;
	private int SAcknowledgment_number = 0;
	private int SSYN = 0;
	private int SACK = 0;
	private int SFIN = 0;
	
	// R means S->R, Receive message from Sender.
	private int client_isn = 0;
	
	private int RSequence_number = 0;
	private int RAcknowledgment_number = 0;
	private int RSYN = 0;
	private int RACK = 0;
	private int RFIN = 0;
	
	private String state = null;
	
	public Receiver(String[] args)
	{
		try
		{
			// receiver_host_ip = need to get the first STPpacket.
			receiver_port = Integer.parseInt(args[0]);	
			file_name = args[1].toString();
			file_path = System.getProperty("user.dir")+"\\"+file_name;
			
			buffer = new Vector();
			buffer_key = new Vector();
			
//			start_time = System.nanoTime();
			run_time = System.nanoTime();
			log = new StringBuffer();
			
			log.append(new String().format("<snd/rcv> <time> <type of packet> <seq-number> <number-ofbytes> <ack-number>%n"));
			
			head_length = 19;	// STP head length. TCP is 20.
			
			ReceiverSocket = new DatagramSocket(receiver_port);
			state = "GO";
			
			System.out.println("Receiver is running.");
		}
		catch (Exception error)
		{
			System.out.println("Error in initiate a receiver.");
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
	
	// From head to SAASF.
	public int[] STPhead(String head)
	{
		int[] SASAF = {Integer.parseInt(head.substring(0, 8)), Integer.parseInt(head.substring(8, 16)), Integer.parseInt(head.substring(16, 17)), Integer.parseInt(head.substring(17, 18)), Integer.parseInt(head.substring(18, 19))};
		return SASAF;
	}
	
	// add head to datagramPacket.
	public DatagramPacket STPpacket(String head, String data)
	{
		byte[] sendData;
		
//		System.out.println(head+" "+data);
		if (data == null || "".equals(data))
		{
			sendData = (head + "\r").getBytes();
//			System.out.println("data is empty: "+sendData.length);
		}
		else
		{
			sendData = (head + data).getBytes();
//			System.out.println("data is not empty: "+sendData.length);
		}
		DatagramPacket STPpacket = new DatagramPacket(sendData, sendData.length, sender_host_ip, sender_port);
		return STPpacket;
	}
	
	// From datagramPacket to head + data.
	public String[] STPpacket(DatagramPacket packet) throws Exception
	{
		String line = new String(packet.getData(), "UTF-8");
		
		String[] head_payload = {null, null};
		
//		System.out.println(line+" "+line.length()+" "+packet.getLength());
		
		head_payload[0] = line.substring(0, 19);
		if (packet.getLength() > 19)
		{
			head_payload[1] = line.substring(19, packet.getLength());
		}

		return head_payload;
	}
	
	public void send_packet(DatagramPacket STPpacket) throws Exception
	{
		BigDecimal m_time = BigDecimal.valueOf(System.nanoTime()-start_time,6);
		String log_snd = null;
		
		ReceiverSocket.send(STPpacket);
		
		if (STPhead(STPpacket(STPpacket)[0])[2] == 1 && STPhead(STPpacket(STPpacket)[0])[3] == 1)
		{
			// SYN&ACK = 1
			log_snd = new String().format("snd  % 9.2f SA % 8d    0 % 8d", m_time, STPhead(STPpacket(STPpacket)[0])[0], STPhead(STPpacket(STPpacket)[0])[1]);
			
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
		
		log.append(log_snd+"\n");
		System.out.println(log_snd);
	}
	
	public DatagramPacket receive_packet() throws Exception
	{
		byte[] receiveData = new byte[1024];
		DatagramPacket request = new DatagramPacket(receiveData, receiveData.length);
		
		// waiting. no try catch for time out.
		// waiting for a centry.
		ReceiverSocket.receive(request);
		
		sender_host_ip = request.getAddress();
		sender_port = request.getPort();
		
		String head = STPpacket(request)[0];
		RSequence_number = STPhead(head)[0];
		RAcknowledgment_number = STPhead(head)[1];
		RSYN = STPhead(head)[2];
		RACK = STPhead(head)[3];
		RFIN = STPhead(head)[4];

		if (start_time == 0)
		{
			start_time = System.nanoTime();
			BigDecimal r_time = BigDecimal.valueOf(start_time-run_time,6);
			System.out.println(new String().format("Server waiting: % 10.2f", r_time));
		}
		
		BigDecimal m_time = BigDecimal.valueOf(System.nanoTime()-start_time,6);
		String log_rcv = null;
		
		if (RSYN == 1)
		{
			// SYN = 1
			log_rcv = new String().format("rcv  % 9.2f  S % 8d % 4d % 8d", m_time, STPhead(STPpacket(request)[0])[0], 0, STPhead(STPpacket(request)[0])[1]);
			
		}
		else if (RACK == 1)
		{
			// ACK = 1
			log_rcv = new String().format("rcv  % 9.2f  A % 8d % 4d % 8d", m_time, STPhead(STPpacket(request)[0])[0], 0, STPhead(STPpacket(request)[0])[1]);
			
		}
		else if (RFIN == 1)
		{
			// FIN == 1
			log_rcv = new String().format("rcv  % 9.2f  F % 8d % 4d % 8d", m_time, STPhead(STPpacket(request)[0])[0], 0, STPhead(STPpacket(request)[0])[1]);
			
		}
		else
		{
			// it must be a data
			log_rcv = new String().format("rcv  % 9.2f  D % 8d % 4d % 8d", m_time, STPhead(STPpacket(request)[0])[0], STPpacket(request)[1].length(), STPhead(STPpacket(request)[0])[1]);
			
		}
		
		log.append(log_rcv+"\n");
		System.out.println(log_rcv);
		
//		System.out.println("Receive: "+STPhead(head)[0]+"|"+STPhead(head)[1]+"|"+STPhead(head)[2]+"|"+STPhead(head)[3]+"|"+STPhead(head)[4]);
		return request;
	}
	
	
	public void STP_connect(int isn) throws Exception
	{
		server_isn = isn;
		
		while ("LISTEN".equals(state))
		{
			
			DatagramPacket request = receive_packet();
			//----------------------------------------------------------------------------
			if (RSYN != 1)
			{
				continue;
			}
			
			client_isn = RSequence_number;

			String send_head = STPhead(server_isn, client_isn+1, 1, 1, 0);
			DatagramPacket hs2_send_packet = STPpacket(send_head, null);
			
			send_packet(hs2_send_packet);
			
			payload_buf = new StringBuffer();
			
			state = "ESTABLISHED";
		}
	}
	public void STP_transmit() throws Exception
	{
		int ns = client_isn+1;
		SAcknowledgment_number = client_isn+1;
		
		int last_index = 0;
		
		while ("ESTABLISHED".equals(state))
		{
			DatagramPacket request = receive_packet();
			//-----------------------------------------------------------------------------
			if (RACK == 1)
			{
				System.out.println("Receive a SYN&ACK");
				continue;
			}
			if (RFIN == 1)
			{
				state = "FINISH";
				continue;
			}
			if (buffer_key.indexOf(RSequence_number) != -1)
			{
				// log 
				N ++;
				
				System.out.println("Receive a duplic Sequence_number: "+RSequence_number);
				continue;
			}
			
			String head = STPpacket(request)[0];
			String payload = STPpacket(request)[1];
			
			PacketBuffer receive = new PacketBuffer();
			
			// log
			NDSR ++;
			
			receive.index = RSequence_number;
			receive.data = payload;
			
//			System.out.println("Receive a packet: "+STPhead(head)[0]+"|"+STPhead(head)[1]+"|"+STPhead(head)[2]+"|"+STPhead(head)[3]+"|"+STPhead(head)[4]);
			
			if (ns != RSequence_number || buffer.size() == 0 || buffer_key.size() == 0)
			{
				buffer_key.add(receive.index);
				buffer.addElement(receive);
			}
			else
			{
				buffer_key.add(receive.index);
				buffer.insertElementAt(receive, last_index);
			}
			
			int i = last_index;
			while ( i < buffer.size())
			{
				PacketBuffer temp = (PacketBuffer) buffer.get(i);
				if (temp.index == ns)
				{
					ns = temp.index + temp.data.length();
					last_index ++;
				}
				i ++;
			}
			
			String hs2_send_head = STPhead(server_isn, ns, 0, 1, 0);
			DatagramPacket hs2_send_packet = STPpacket(hs2_send_head, null);
			
			send_packet(hs2_send_packet);
			
			
		}
	}
	public void STP_finish() throws Exception
	{
		if ("FINISH".equals(state))
		{
			System.out.println("time to finish");
			// send ack
			String send_head = STPhead(server_isn+1, RSequence_number+1, 0, 1, 0);
			DatagramPacket finack_packet = STPpacket(send_head, null);
			send_packet(finack_packet);
			
			// store data.
			for (int i = 0; i < buffer.size(); i++)
			{
				PacketBuffer temp = (PacketBuffer) buffer.get(i);
				payload_buf.append(temp.data);
			}
			
			File f = new File(file_name);
			FileWriter fw = new FileWriter(f.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
			bw.write(payload_buf.toString());
			bw.close();
			// send fin
			
			String fin_head = STPhead(server_isn+1, RSequence_number+1, 0, 0, 1);
			DatagramPacket fin_packet = STPpacket(fin_head, null);
			send_packet(fin_packet);
			
//			Amount of Data Received (in bytes)
//			Number of Data Segments Received
//			Number of duplicate segments received (if any)
			BigDecimal end_time = BigDecimal.valueOf(System.nanoTime()-start_time,6);
			String log_end = null;
			log_end = new String("Amount of Data Received: "+payload_buf.length()+"\n");
			log_end += new String("Number of Data Segments Received: "+NDSR+"\n");
			log_end += new String("Number of duplicate segments received: "+N+"\n");
			log.append(log_end);
			
			File f2 = new File("Receiver_log.txt");
			FileWriter fw2 = new FileWriter(f2.getAbsoluteFile());
			BufferedWriter bw2 = new BufferedWriter(fw2);
			bw2.write(log.toString());
			bw2.close();
			
			// close
			state = "CLOSED";
		}
	}
	
	public static void main(String[] args) throws Exception
	{
		if (args.length != 2)
		{
			System.out.println("Required arguments should be 2. You must foget something.");
			return;
		}
		
		Receiver bb = new Receiver(args);
		
		bb.state = "LISTEN";
		
		bb.STP_connect(1);
		bb.STP_transmit();
		bb.STP_finish();
		
		bb.ReceiverSocket.close();

	}
}