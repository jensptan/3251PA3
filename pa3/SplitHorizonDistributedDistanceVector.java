import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.TreeMap;

public class SplitHorizonDistributedDistanceVector {
	private static int nRouter;
	private static int[][][] routingTables; //[router][source][destination] -> distance
	private static int[][][] forwardingTables; //[router][destination][details] -> for each router/destination pair, there are 3 detail entries: destination, cost, nextHop
	private static int[][][] broadcasts; //[router][source][distance vector]
	private static HashMap<Integer, ArrayList<Event>> topologicalEvents = new HashMap<>();
	private static final int INF = Integer.MAX_VALUE / 2;
	private static HashMap<Integer, HashMap<Integer, Integer>> neighbors = new HashMap<>();
	private static boolean changed;
	private static int lastEvent = 0;
	private static int convergenceDelay;

	//Initiates the Routing Table and Neighbors map for each router.
	//If the router is the same as the destination, the distance is 0.
	//If the router is different from the destination, the distance is unknown (INF).
	private static void initRoutingTables() {
		routingTables = new int[nRouter + 1][nRouter + 1][nRouter + 1];
		for (int router = 1; router <= nRouter; ++router) {
			for (int source = 1; source <= nRouter; ++source) {
				for (int destination = 1; destination <= nRouter; ++destination) {
					if (source == destination) {
						routingTables[router][source][destination] = 0;
					}
					else {
						routingTables[router][source][destination] = INF;
					}
				}
			}
			neighbors.put(router, new HashMap<>());
		}
	}

	//Initiates the Forwarding Table for each router.
	//If the router is the same as the destination, the distance is 0, and the next hop is the router.
	//If the router is the same as the destination, the distance is unknown (INF), and the next hop is the router.
	private static void initForwardingTables() {
		//Router #'s start at 1, not 0. Arrays need to be nRouter +1 long
		forwardingTables = new int[nRouter + 1][nRouter + 1][3];
		for (int router = 1; router <= nRouter; ++router) {
			for (int destination = 1; destination <= nRouter;  ++destination) {
				if (destination != router) {
					forwardingTables[router][destination][0] = destination;
					forwardingTables[router][destination][1] = INF;
				} else {
					forwardingTables[router][destination][0] = destination;
					forwardingTables[router][destination][1] = 0;
					forwardingTables[router][destination][2] = destination;
				}
			}
		}
	}

	//Reads in initial topology.
	//First line is number of routers in the network.
	//Following lines are links and their weights. Links are noted by their source and dest routers. Router numbers start at 1.
	//The text file will not contain any misformatting.
	private static void takeInitialTopology(String fileName) throws Exception {
		Scanner scanner = new Scanner(new File(fileName));
		nRouter = scanner.nextInt();
		//Now that we know how many routers are in the network, we can initiate the Routing and Forwarding tables.
		initRoutingTables();
		initForwardingTables();
		while (scanner.hasNext()) {
			//Router #'s start at 1, not 0.
			int source = scanner.nextInt();
			int destination = scanner.nextInt();
			int weight = scanner.nextInt();
			//If weight is -1, disregard. No link is being created.
			//Only add links with weights > 0.
			if (weight > 0) {
				routingTables[source][source][destination] = weight;
				routingTables[destination][destination][source] = weight;
				neighbors.get(source).put(destination, weight);
				neighbors.get(destination).put(source, weight);
				forwardingTables[source][destination][1] = weight;
				forwardingTables[source][destination][2] = destination;
			}
		}
		scanner.close();
	}

	//Reads in the topological events.
	//Topological events are changes in links, associated with a round.
	//Links can be added, have their weights changed, or be removed (weight < 0).
	//Also determines the last round at which a topological event occurs.
	private static void takeTopologicalEvents(String fileName) throws Exception {
		Scanner scanner = new Scanner(new File(fileName));
		while (scanner.hasNext()) {
			//Router #'s start at 1, not 0.
			int round = scanner.nextInt();
			int source = scanner.nextInt();
			int destination = scanner.nextInt();
			int weight = scanner.nextInt();
			//If weight is -1, cost should be set to INF.
			if (weight < 0 ) {
				weight = INF;
			}
			if (round > lastEvent) {
				lastEvent = round;
			}

			// If the round exists, we just need to add to the list for it, other wise we create the list
			if (topologicalEvents.containsKey(round)) {
				topologicalEvents.get(round).add(new Event(source, destination, weight));
			} else {
				topologicalEvents.put(round, new ArrayList<>());
				topologicalEvents.get(round).add(new Event(source, destination, weight));
			}
		}
		scanner.close();
	}

	//Called at the beginning of a round to enact any Topological Events for that round.
	//A topological event is enacted
	//event shouldn't be applied to routingTables[]? should be applied to neighbors[]?
	private static boolean updateTopology(int round) {
		if (topologicalEvents.containsKey(round)) {
			for (Event event: topologicalEvents.get(round)) {
				//update neighbors HashMap with link updates from event (removal, new link, or updated weight)
				int source = event.source;
				int destination = event.destination;
				int weight = event.weight;

				if (weight < 0) {
					//remove the link
					neighbors.get(source).remove(destination);
					neighbors.get(destination).remove(source);
				} else {
					//update the link
					neighbors.get(source).put(destination, weight);
					neighbors.get(destination).put(source, weight);
				}
			}
			changed = true;
			return true;
		}
		return false;
	}

	//Simulates the "broadcasting" of each router's distance vector to its neighbors.
	//Each router has a nRouter + 1 x nRouter + 1 array, indices 1 to nRouter will be used.
	//A router's broadcast array will hold the distance vectors its neighbors hav emost recently sent to it.
	//(from the end of the most recent round).
	//These broadcast arrays will be read and applied at the beginning of the following round.
	//NOTE: *might* be replacing updateNeighbors(). But broadcast() will not mix already updated vectors with vectors sent to neighbors.
	//This keeps the broadcasted vectors safe from the vector updates at the beginning of the next round.
	//TODO: this method will be where the variations diverge. This should be the only code that is different btwn the variations.
	private static void broadcast() {
		broadcasts = new int[nRouter + 1][nRouter + 1][nRouter + 1];

		for (int router = 1; router <= nRouter; ++router) {
			for (int source = 1; source <= nRouter; ++source) {
	            for (int destination = 1; destination <= nRouter; ++destination) {
	            	broadcasts[router][source][destination] = INF;
	            }
			}
		}

		//loop thru routers
		for (int router = 1; router <= nRouter; router++) {
			//loop thru router's neighbors
			for (int neighbor: neighbors.get(router).keySet()) {
				//loop thru destination in router's distance vector.
				for (int destination = 1; destination <= nRouter; destination++) {
					//set the router's row in the neighbor's broadcast array (which receives the broadcasts sent to said neighbor) to the router's distance vector.
                    if (neighbor == forwardingTables[router][destination][2] && neighbor != destination) {
                        // neighbor is the next hop for the destination so we don't broadcast
                    } else {
                        // Other wise we broadcast
                        broadcasts[neighbor][router][destination] = routingTables[router][router][destination];
                    }
				}
			}
		}
	}

	//Prints each router's routing table and convergence delay
	private static void printBroacasts() {
		for (int router = 1; router <= nRouter; ++router) {
			System.out.println("broadcast table for router " + (router));
			for (int source = 1; source <= nRouter; ++source) {
				for (int destination = 1; destination <= nRouter; ++destination) {
					if (broadcasts[router][source][destination] < INF) {
						System.out.format("%3d  ", broadcasts[router][source][destination]);
					}
					else {
						System.out.print("inf  ");
					}
				}
				System.out.println();
			}
			System.out.println("-- -- -- -- --");
		}
		System.out.println();
		System.out.println("## ## ## ## ##");
		System.out.println();
	}


	//At the beginning of a round, update the routers' routing tables with their neightbors' distance vectors.
	//The only rows in a router's routing table that should be changed are those of their neighbors.
	//TODO: Don't think this is working properly. Routers should broadcast their distance vectors at the end of a round,
	//TODO: this seems to update vectors with updated vectors from the next round.
	private static void updateNeighbors() {
		for (int router = 1; router <= nRouter; ++router) {
			for (int neighbor: neighbors.get(router).keySet()) {
	            for (int destination = 1; destination <= nRouter; ++destination) {
	            	routingTables[router][neighbor][destination] = routingTables[neighbor][neighbor][destination];
	            }
			}
		}
	}

	//TODO: update routers based on broadcasted distance vectors.
	private static void updateSelf() {
		changed = false;
		for (int router = 1; router <= nRouter; ++router) {
			for (int destination = 1; destination <= nRouter; ++destination) {
				int currentDistance = routingTables[router][router][destination];
				int minDistance = currentDistance;
				for (int neighbor: neighbors.get(router).keySet()) {
					minDistance = Math.min(minDistance, neighbors.get(router).get(neighbor) + broadcasts[router][neighbor][destination]);
					routingTables[router][neighbor][destination] = broadcasts[router][neighbor][destination];
					routingTables[router][router][destination] = minDistance;
					if (currentDistance != minDistance) {
						// Updates forwarding table
						forwardingTables[router][destination][1] = minDistance;
						forwardingTables[router][destination][2] = neighbor;
						changed = true;
						//TODO: remove print statement
//						if (router == 2) {
//							System.out.println("Trying to understand 2. Destination: " + destination + " Neighbor: " + neighbor + " \nDist To Neighbor: " + neighbors.get(router).get(neighbor) + " Neighbor to Dest: " + broadcasts[router][neighbor][destination] + " \nCurrent Dist: " + currentDistance + " New Dist: " + minDistance + "\n");
//						}
						currentDistance = minDistance;
					}


				}
			}
		}
	}

	private static void printForwardingTables() {
		for (int router = 1; router <= nRouter; ++router) {
			System.out.println("Forwarding table at router " + (router));
			for (int destination = 1; destination <= nRouter; ++destination) {
				for (int i = 0; i < 3; i++) {
					if (forwardingTables[router][destination][i] < INF) {
						System.out.format("%3d  ", forwardingTables[router][destination][i]);
					}
					else {
						System.out.print("inf  ");
					}
				}
				System.out.println();
			}
			System.out.println("-- -- -- -- --");
		}
		System.out.println();
		System.out.println("## ## ## ## ##");
		System.out.println();
	}

	//Prints each router's routing table and convergence delay
	private static void printTables() {
		for (int router = 1; router <= nRouter; ++router) {
			System.out.println("Routing table at router " + (router));
			for (int source = 1; source <= nRouter; ++source) {
				for (int destination = 1; destination <= nRouter; ++destination) {
					if (routingTables[router][source][destination] < INF) {
						System.out.format("%3d  ", routingTables[router][source][destination]);
					}
					else {
						System.out.print("inf  ");
					}
				}
				System.out.println();
			}
			System.out.println("-- -- -- -- --");
		}
		System.out.println();
		System.out.println("Convergence delay: " + convergenceDelay);
		System.out.println("## ## ## ## ##");
		System.out.println();
	}

	//Runs the distance vector protocol
	private static void runDistanceVector(int mode) {
		if (mode == 1) {
			convergenceDelay = 0;
			// Just so we handle any events at round 0
			if (updateTopology(0)) {
			}
			System.out.println("Round " + 0);
			printTables();
			broadcast();
			for (int round = 1; ; ++round) {
				if (updateTopology(round)) {
					convergenceDelay = 0;
				} else {
					convergenceDelay++;
				}
				printBroacasts();
				//updateNeighbors();
				//TODO: process the distance vectors broadcast from neighbors
				//2 parts: update neighbors' distance vectors in routing table, then determine the router's new distance vector based on this info.
				updateSelf();
				System.out.println("Round " + round);
				printTables();
				printForwardingTables();
				//TODO: only print convergence delay after final routing table
				//TODO: forwarding tables aren't always matching routing tables' outputs for distance. Hmm.
				//If nothing's changed and we've passed the last topological event, we've converged!
				if ((changed == false) && (round >= lastEvent)) {
					break;
				}
				//Check for count-to-infinity
				if (countToInfinityCheck()) {
					//TODO: waiting to hear from profs about a preferred way of handling this
					System.out.println("Count-to-infinity conditions detected.");
					return;
				}
				//broadcast new distance vector to neighbors, but do not process the distance vectors received yet.
				broadcast();
			}
		} else if (mode == 0) {
			// Just so we handle any events at round 0
			if (updateTopology(0)) {
			}
			convergenceDelay = 0;
			broadcast();
			int round;
			for (round = 1; ; ++round) {
				if (updateTopology(round)) {
					convergenceDelay = 0;
				} else {
					convergenceDelay++;
				}
				//updateNeighbors();
				//TODO: process the distance vectors broadcast from neighbors
				//2 parts: update neighbors' distance vectors in routing table, then determine the router's new distance vector based on this info.
				updateSelf();

				//If nothing's changed and we've passed the last topological event, we've converged!
				if ((changed == false) && (round >= lastEvent)) {
					break;
				}
				//Check for count-to-infinity
				if (countToInfinityCheck()) {
					//TODO: waiting to hear from profs about a preferred way of handling this
					System.out.println("Count-to-infinity conditions detected.");
					return;
				}
				//broadcast new distance vector to neighbors, but do not process the distance vectors received yet.
				broadcast();
			}
			System.out.println("Round " + round);
			printTables();
		} else {
			System.out.println("Please use a valid mode:");
			System.out.println("1 for a detailed output of each round");
			System.out.println("0 for the final routing tables and convergenceDelay");
		}
	}

	//Checks to see if any of the routers' distances has exceeded 100.
	//Returns true if count-to-infinity detected.
	//Returns false if count-to-infinity not detected.
	private static boolean countToInfinityCheck() {
		int distance;
		//Loop thru routers' routing tables
		for (int router = 1; router <= nRouter; router++) {
			//Loop thru router's  routing table columns (destinations).
			for(int destination = 1; destination <= nRouter; destination++) {
				//router's routing table; source = router; dest = destination.
				distance = routingTables[router][router][destination];
				if (distance > 100 && distance != INF) {
					//count-to-infinity detected.
					return true;
				}
			}
		}
		//No count-to-infinity detected.
		return false;
	}

	//Execution of the Basic Protocol.
	public static void main(String[] args) throws Exception {
		if ((args.length < 2) || (args.length > 3)) {
			System.out.println("Invalid input. Parameters: <Initial Topology .txt file> <Topological Events .txt file> <optional: 0/1 detailed mode>");
			return;
		}
		//Check that args 0 and 1 are .txt files
		if ((!args[0].substring(Math.max(0, args[0].length() - 4)).equals(".txt")) || (!args[1].substring(Math.max(0, args[1].length() - 4)).equals(".txt"))) {
			System.out.println(args[0].substring(Math.max(0, args[0].length() - 4)));
			System.out.println(args[1].substring(Math.max(0, args[1].length() - 4)));
			System.out.println("Invalid input. Parameters: <Initial Topology .txt file> <Topological Events .txt file> <optional: 0/1 detailed mode>");
			return;
		}
		takeInitialTopology(args[0]);
		takeTopologicalEvents(args[1]);
		if (args.length == 2) {
			//If no mode input, assume 0 (less detailed).
			runDistanceVector(0);
		} else {
			runDistanceVector(Integer.parseInt(args[2]));
		}

	}

	public static class Event {
		int source;
		int destination;
		int weight;

		public Event(int source, int destination, int weight) {
			this.source = source;
			this.destination = destination;
			this.weight = weight;
		}
	}
}
