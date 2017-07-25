Names: Jacob Hayes and Jensen Tanner
Emails: jhayes42@gatech.edu
Date: July 21st, 2017
Assignment: Summer 2017 CS 3251 Programming Assignment 3: Distance Vector Routing

Names and Descriptions of Files:
================================
DistributedDistanceVector.java
    - Implements the basic distance vector protocol, in which each router sends its complete distance vector to all of its neighbors.

SplitHorizonDistributedDistanceVector.java
    - Implements the split horizon variation in which router X does not send to router Y its distance to destination Z, if Y is the next-hop that X uses to reach Z.

PoisonReverseDistributedDistanceVector.java
    - Implements the split horizon with poison reverse variation: as in “split horizon” but router X sends a cost of “infinity” to router Y.

InitialTopology.txt
    - Initial Topology of the network

TopologicalEvents.txt
    - Topological events that occur at given rounds. An event can be the addition, deletion, or modify a link.

README.txt
    - Names and descriptions of the files included, insructiuons for compiling and running, and known bugs or limitations (if any).

sample.txt
    - Sample output.



Instructions for Compiling and Running:
=======================================
    Our simulators were written in Java, so to compile one use:

        javac <DistanceVectorVariation>.java

    To run, use the following command:

        java <DistanceVectorVariation> <InitialTopology> <TopologicalEvents> <mode>

        * InitialTopology and TopologicalEvents must be .txt files in the specified format given in the project description
        * mode is a binary flag
          - 0 will give the final routing tables for each router along with the convergence delay since the last topological event.
          - 1 will give a detailed output with the routing tables for each router each round along with the convergence delay.


Known Bugs or Limitations:
