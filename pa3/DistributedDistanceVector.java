import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;

public class DistributedDistanceVector {
	private static int nRouter;
	private static int[][][] routingTables;
	private static final int INF = Integer.MAX_VALUE / 2;
	private static HashMap<Integer, HashSet<Integer>> neighbors = new HashMap();
	private static boolean changed;
	
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
			neighbors.put(router, new HashSet());
		}
	}
	
	private static void takeInput(String fileName) throws Exception {
		Scanner scanner = new Scanner(new File(fileName));
		nRouter = scanner.nextInt();
		initRoutingTables();
		while (scanner.hasNext()) {
			int source = scanner.nextInt() - 1;
			int destination = scanner.nextInt() - 1;
			int weight = scanner.nextInt();
			routingTables[source][source][destination] = weight;
			routingTables[destination][destination][source] = weight;
			neighbors.get(source).add(destination);
			neighbors.get(destination).add(source);
		}
		scanner.close();
	}
	
	private static void updateNeighbors() {
		for (int router = 0; router < nRouter; ++router) {
			for (int neighbor: neighbors.get(router)) {
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
				for (int neighbor: neighbors.get(router)) {
					minDistance = Math.min(minDistance, routingTables[router][router][neighbor] + routingTables[router][neighbor][destination]);
				}
				routingTables[router][router][destination] = minDistance;
				if (currentDistance != minDistance) {
					changed = true;
				}
			}
		}
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
		System.out.println("## ## ## ## ##");
		System.out.println();
	}
	
	private static void runDistanceVector() {
		System.out.println("Round " + 0);
		printTables();
		for (int round = 1; ; ++round) {
			updateNeighbors();
			updateSelf();
			System.out.println("Round " + round);
			printTables();
			if (changed == false) {
				break;
			}
		}
	}
	
	public static void main(String[] args) throws Exception {
		takeInput(args[0]);
		runDistanceVector();
	}
}
