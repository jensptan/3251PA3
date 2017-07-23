import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.TreeMap;

public class DistributedDistanceVector {
	private static int nRouter;
	private static int[][][] routingTables;
	private static int[][][] forwardingTables;
	private static HashMap<Integer, ArrayList<Event>> topologicalEvents = new HashMap();
	private static final int INF = Integer.MAX_VALUE / 2;
	private static HashMap<Integer, HashMap<Integer, Integer>> neighbors = new HashMap();
	private static boolean changed;
	private static int lastEvent = 0;
	private static int convergenceDelay;

	private static void initRoutingTables() {
		routingTables = new int[nRouter][nRouter][nRouter];
		for (int router = 0; router < nRouter; ++router) {
			for (int source = 0; source < nRouter; ++source) {
				for (int destination = 0; destination < nRouter; ++destination) {
					if (source == destination) {
						routingTables[router][source][destination] = 0;
					}
					else {
						routingTables[router][source][destination] = INF;
					}
				}
			}
			neighbors.put(router, new HashMap());
		}
	}

	private static void initForwardingTables() {
		forwardingTables = new int[nRouter][nRouter - 1][3];
		for (int router = 0; router < nRouter; ++router) {
			for (int destination = 0; destination < nRouter;  ++destination) {
				if (destination != router && destination < router) {
					forwardingTables[router][destination][0] = destination;
					forwardingTables[router][destination][1] = INF;
				} else if (destination != router && destination > router) {
					forwardingTables[router][destination - 1][0] = destination;
					forwardingTables[router][destination - 1][1] = INF;
				}
			}
		}
	}

	private static void takeInitialTopology(String fileName) throws Exception {
		Scanner scanner = new Scanner(new File(fileName));
		nRouter = scanner.nextInt();
		initRoutingTables();
		initForwardingTables();
		while (scanner.hasNext()) {
			int source = scanner.nextInt() - 1;
			int destination = scanner.nextInt() - 1;
			int weight = scanner.nextInt();
			routingTables[source][source][destination] = weight;
			routingTables[destination][destination][source] = weight;
			neighbors.get(source).put(destination, weight);
			neighbors.get(destination).put(source, weight);
		}
		scanner.close();
	}

	private static void takeTopologicalEvents(String fileName) throws Exception {
		Scanner scanner = new Scanner(new File(fileName));
		while (scanner.hasNext()) {
			int round = scanner.nextInt();
			int source = scanner.nextInt() - 1;
			int destination = scanner.nextInt() - 1;
			int weight = scanner.nextInt();
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
				topologicalEvents.put(round, new ArrayList());
				topologicalEvents.get(round).add(new Event(source, destination, weight));
			}
		}
		scanner.close();
	}

	private static boolean updateTopology(int round) {
		if (topologicalEvents.containsKey(round)) {
			for (Event event: topologicalEvents.get(round)) {
				routingTables[event.source][event.source][event.destination] = event.weight;
			}
			changed = true;
			return true;
		}
		return false;
	}

	private static void updateNeighbors() {
		for (int router = 0; router < nRouter; ++router) {
			for (int neighbor: neighbors.get(router).keySet()) {
	            for (int destination = 0; destination < nRouter; ++destination) {
	            	routingTables[router][neighbor][destination] = routingTables[neighbor][neighbor][destination];
	            }
			}
		}
	}

	private static void updateSelf() {
		changed = false;
		for (int router = 0; router < nRouter; ++router) {
			for (int destination = 0; destination < nRouter; ++destination) {
				int currentDistance = routingTables[router][router][destination];
				int minDistance = currentDistance;
				for (int neighbor: neighbors.get(router).keySet()) {
					minDistance = Math.min(minDistance, routingTables[router][router][neighbor] + routingTables[router][neighbor][destination]);
				}
				routingTables[router][router][destination] = minDistance;
				if (currentDistance != minDistance) {
					changed = true;
				}
			}
		}
	}

	private static void updateForwardingTables() {

	}

	private static void printForwardingTables() {
		for (int router = 0; router < nRouter; ++router) {
			System.out.println("Forwarding table at router " + (router + 1));
			for (int destination = 0; destination < nRouter - 1; ++destination) {
				for (int i = 0; i < 3; i++) {
					if (forwardingTables[router][destination][i] < INF) {
						System.out.format("%3d  ", forwardingTables[router][destination][i]);
					}
					else {
						System.out.print("inf  ");
					}
				}
				System.out.println("-- -- -- -- --");
			}
		}
		System.out.println();
		System.out.println("## ## ## ## ##");
		System.out.println();
	}

	private static void printTables() {
		for (int router = 0; router < nRouter; ++router) {
			System.out.println("Routing table at router " + (router + 1));
			for (int source = 0; source < nRouter; ++source) {
				for (int destination = 0; destination < nRouter; ++destination) {
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

	private static void runDistanceVector(int mode) {
		if (mode == 1) {
			convergenceDelay = 0;
			System.out.println("Round " + 0);
			printTables();
			for (int round = 1; ; ++round) {
				if (updateTopology(round)) {
					convergenceDelay = 0;
				} else {
					convergenceDelay++;
				}
				updateNeighbors();
				updateSelf();
				System.out.println("Round " + round);
				printTables();
				if (changed == false && round >= lastEvent) {
					break;
				}
			}
		} else if (mode == 0) {
			convergenceDelay = 0;
			int round;
			for (round = 1; ; ++round) {
				if (updateTopology(round)) {
					convergenceDelay = 0;
				} else {
					convergenceDelay++;
				}
				updateNeighbors();
				updateSelf();
				if (changed == false) {
					break;
				}
			}
			System.out.println("Round " + round);
			printTables();
		} else {
			System.out.println("Please use a valid mode:");
			System.out.println("1 for a detailed output of each round");
			System.out.println("0 for the final routing tables and convergenceDelay");
		}
	}

	public static void main(String[] args) throws Exception {
		takeInitialTopology(args[0]);
		takeTopologicalEvents(args[1]);
		runDistanceVector(Integer.parseInt(args[2]));
	}

	public static class Event {
		public int source;
		public int destination;
		public int weight;

		public Event(int source, int destination, int weight) {
			this.source = source;
			this.destination = destination;
			this.weight = weight;
		}
	}
}
