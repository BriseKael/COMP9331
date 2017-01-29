
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;
import java.util.regex.*;

class neighbour
{
	public String name = null;
	public float cost = 0;
	
	public int port = 0;
	
	// 0 is dead, 1 is alive
	public int isalive = 0;
	
	public long alivetime = 0;
	
	public neighbour(String name, float cost)
	{
		this.name = name;
		this.cost = cost;
	}
}




public class Lsr 
{
	private static int UPDATE_INTERVAL = 1000;
	private static int ROUTE_UPDATE_INTERVAL = 30000;
	
	private String node_id;
	private InetAddress node_ip;
	private int node_port;
	private String config_text_path;
	
	private DatagramSocket socket;
	
	private File file;
	
	private int client_id;
	
	private HashMap<String, Vector<Integer>> broadcasts;
	
	// sendmessage format: node_id+"|"+neighbour.name+" "+neighbour.cost+" "+neighbour.port+"|"+....
	private Vector<neighbour> neighbours;
	private HashMap<String, Vector<neighbour>> graphs;
	
	private void init(String[] args)
	{
		try
		{
			this.node_id = args[0].toString();
			this.node_ip = InetAddress.getByName("127.0.0.1");
			this.node_port = Integer.parseInt(args[1]);
			this.config_text_path = args[2].toString();
			
			this.socket = new DatagramSocket(this.node_port);
			this.socket.setSoTimeout(1000);
			
			this.file = new File(this.config_text_path);
			
			this.client_id = 0;
			
			this.neighbours = new Vector<neighbour>();
			
			this.graphs = new HashMap<String, Vector<neighbour>>();
			
			Vector<Integer> ids = new Vector<Integer>();
			
			this.broadcasts = new HashMap<String, Vector<Integer>>();
			
			try
			{
				Scanner read = new Scanner(this.file);
				
				int neighbour_number = read.nextInt();
				
				while (read.hasNext())
				{
					String name = read.next();
					if (name.length() == 0)
					{
						break;
					}
					float cost = read.nextFloat();
					int port = read.nextInt();
					
					neighbour neighbour = new neighbour(name, cost);
					
					neighbour.port = port;
					
					neighbour.isalive = 1;
					neighbour.alivetime = System.currentTimeMillis();
					
					this.neighbours.addElement(neighbour);
				}


				read.close();
			}
			catch (Exception e)
			{
				System.out.println(e);
				return;
			}
			
		}	
		catch (Exception e)
		{
			System.out.println(e);
			return;
		}
	}
	
	private void sendtoneighbours(String sendmessage, int sendportnumber) throws Exception
	{
		
//		System.out.println(sendmessage);
		for (neighbour neighbour : this.neighbours)
		{
			if (neighbour.port != sendportnumber)
			{
				byte[] sendData = new byte[1024];
				sendData = sendmessage.getBytes();
				DatagramPacket sendpacket = new DatagramPacket(sendData, sendData.length, this.node_ip, neighbour.port);
				
				this.socket.send(sendpacket);
			}
		}
	}
	
	
	public static void main(String[] args) throws Exception
	{	
		// new Lsr, named self.
		Lsr self = new Lsr();
		self.init(args);
		
		int looptimes = 0;
		// the loop.
		while (true)
		{
			
			looptimes++;
			// 1. first 30 seconds, send to neighbours and receive message.
			
			int sendtimes = 0;
			int resendtimes = 0;
			
			long timer = System.currentTimeMillis();
			long selfsendtimer = System.currentTimeMillis();
			
			// empty the graphs and put itself
			self.graphs.clear();
			self.graphs.put(self.node_id, self.neighbours);
			
			while (System.currentTimeMillis() - timer < ROUTE_UPDATE_INTERVAL)
			{
				// send self message to neighbours
				if (System.currentTimeMillis() - selfsendtimer > UPDATE_INTERVAL)
				{
					self.client_id++;
					String sendid = String.format("%08d", self.client_id);
					
					String sendmessage = sendid+"|"+self.node_id+"|";
					
					for (neighbour neighbour : self.neighbours)
					{
						sendmessage += neighbour.name+" "+neighbour.cost+" "+neighbour.port+" "+neighbour.isalive+"|";
					}
					
					self.sendtoneighbours(sendmessage, self.node_port);
					selfsendtimer = System.currentTimeMillis();
					
					sendtimes++;
				}
				
				try
				{
					// receiving message
					byte[] receiveData = new byte[1024];
					DatagramPacket receivepacket = new DatagramPacket(receiveData, receiveData.length);

					//
					//
					// receive
					//
					self.socket.receive(receivepacket);
					// 
					
					int receiveport = receivepacket.getPort();
					String receivemessage = new String(receivepacket.getData(), "UTF-8");
					
//					System.out.println(receivemessage);
					for (neighbour neighbour : self.neighbours)
					{
						if (neighbour.port == receiveport)
						{
							neighbour.alivetime = System.currentTimeMillis();
							neighbour.isalive = 1;
//							System.out.println(neighbour.name+" is alive!!!");
						}
					}
					
					// this is a link state message.
					String broadcastsource = new String();
					int id = Integer.parseInt(receivemessage.split("\\|")[0]);
					broadcastsource = receivemessage.split("\\|")[1];

					// put receive to graph
					
					String information = receivemessage.substring(11);
					String[] parts = information.split("\\|");
					
					Vector<neighbour> other_neighbours = new Vector<neighbour>();
					for (int neighbour_number = 0; neighbour_number < parts.length-1; neighbour_number++)
					{
						String part = parts[neighbour_number];
						
						String name = part.split(" ")[0];
						float cost = Float.parseFloat(part.split(" ")[1]);
						// and port here.
						int alive = Integer.parseInt(part.split(" ")[3]);
						
						neighbour other_neighbour = new neighbour(name, cost);
						other_neighbour.isalive = alive;
						other_neighbours.addElement(other_neighbour);
					}

					self.graphs.put(broadcastsource, other_neighbours);
					
					
					// re send the message
					int find = 0;
					if (self.broadcasts.containsKey(broadcastsource))
					{
						Vector<Integer> ids = self.broadcasts.get(broadcastsource);
						
						if (!ids.contains(id))
						{
							ids.addElement(id);
							self.broadcasts.put(broadcastsource, ids);
						}
						else
						{
							find = 1;
						}
					}
					else
					{
						Vector<Integer> ids = new Vector<Integer>();
						ids.addElement(id);
						self.broadcasts.put(broadcastsource, ids);
					}
					
					if (find == 0)
					{
						resendtimes++;
						self.sendtoneighbours(receivemessage, receiveport);
					}
					
					// end of link state message.
					
					for (neighbour neighbour : self.neighbours)
					{
						if (System.currentTimeMillis() - neighbour.alivetime > 3*UPDATE_INTERVAL)
						{
//							System.out.println(neighbour.name+" is dead!!!");
							neighbour.isalive = 0;
						}
					}
				}
				catch (Exception e)
				{
					// time out here.
					// send self config to neighbours

				}
				
			}
			

			// step 2
			// diajstla
			Vector<String> Vall = new Vector<String>();
			
			Vector<String> Nnn = new Vector<String>();
			
			HashMap<String, Float> Dcost = new HashMap<String, Float>();
			HashMap<String, String> Pcost = new HashMap<String, String>();
			
			for (String Nodes : self.graphs.keySet())
			{
				Vall.addElement(Nodes);
			}
			
			Nnn.addElement(self.node_id);
			
			for (String Nodes : Vall)
			{
				Boolean find = false;
				for (neighbour neighbour : self.neighbours)
				{
					if (Nodes.equals(neighbour.name) && (neighbour.isalive == 1))
					{
						Dcost.put(Nodes, neighbour.cost);
						Pcost.put(Nodes, self.node_id+Nodes);
						find = true;
					}
				}
				if (find == false)
				{
					Dcost.put(Nodes, Float.MAX_VALUE);
				}
			}
			
			while (Nnn.size() < Vall.size())
			{
				float min = Float.MAX_VALUE;
				String w = new String();
				for (String Nodes : Dcost.keySet())
				{
					if (!Nnn.contains(Nodes) && Dcost.get(Nodes) < min)
					{
						min = Dcost.get(Nodes);
						w = Nodes;
					}
				}
//				System.out.println(w);
				Nnn.addElement(w);
				
				if (self.graphs.containsKey(w))
				{
					Vector<neighbour> wnieghbours = self.graphs.get(w);
					for (neighbour v : wnieghbours)
					{
						if (!Nnn.contains(v.name) && (v.isalive == 1) && (Dcost.containsKey(v.name)))
						{
							if (Dcost.get(v.name) > (Dcost.get(w)+v.cost))
							{
								Dcost.put(v.name, (Dcost.get(w)+v.cost));
								Pcost.put(v.name, Pcost.get(w)+v.name);
							}
						}
					}
				}
				
			}
			
//			System.out.println("Looptimes: "+looptimes+", Broadcast self times: "+sendtimes+", Retransmit others times: "+resendtimes);
			System.out.println("Looptimes: "+looptimes);
			for (String Nodes : Dcost.keySet())
			{
				if (Dcost.containsKey(Nodes) && (Dcost.get(Nodes) != Float.MAX_VALUE))
				{
					System.out.println("	least-cost path to node "+Nodes+": "+Pcost.get(Nodes)+" and the cost is "+Dcost.get(Nodes));
				}
			}

		}
		// end of the while true loop
//		self.socket.close();
	}
}
