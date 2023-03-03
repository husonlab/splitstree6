# SplitsTree CE User Documentation

SplitsTree Community Edition is the latest implementation of the SplitsTree program. 

## About the program

SplitsTree is a program for computing and display unrooted and rooted phylogenetic trees and networks.
You can open an input file in several different formats and this will define a set of input taxa and a set of input data.
Input data can be an alignment of sequences, a distance matrix, a collection of trees or rooted networks, or a split network.
The user can menu items to setup and customize the desired analysis. Under the hood, the program maintains a workflow graph that explicity
models the desired analysis. The program provides an alignment view, different visualizations of trees, including tanglegrams and densitrees,
different visualizations of split networks, and some other analysis techniques, such as PCoA and tSNE.

## Getting started

To get started on Linux, MacOS or Windows, download the latest program installer from
[here](https://software-ab.informatik.uni-tuebingen.de/download/splitstree6/welcome.html).

Start the program and open a file of interest. In the installation directory, you will find an examples directory that contains a
large number of example files. For example, open the file example/publications/BryantHuson2023/buttercups-cytochromeC.fasta,
which contains a multiple alignment of cytochrome-c genes from plants, to get this visualization:

![Example](https://github.com/husonlab/splitstree6/blob/main/doc/images/cytochrome-c.png "Neighbor net")

## General design

The program is designed around the the following concepts:
- The user loads a file containing an input dataset, thus defining the "input" taxa and "input" data, which remain unchanged throughout the analysis. The input data may be a multiple-sequence alignment, a distance matrix, one or more trees or rooted networks, a collection of splits or a split network.
- The user may filter the initial data by deactivating selected taxa, alignment columns or input trees, and this produces a set of "working" taxa and data.
- The program performs calculations on the working taxa and data only. Algorithms are attached to the working data, or from data items derived from the working data.
- All analyses associated with a given input dataset are presented together in a single window, using multiple tabs.
- The analyses associated with a given input dataset is represented by a workflow, which is a graph in which nodes either represent data or algorithms, as shown here:

![Workflow](https://github.com/husonlab/splitstree6/blob/main/doc/images/workflow.png "SplitsTreeCE workflow")

Based on the workflow, SplitsTreeCE generates a "methods section" that describes the data and which algorithms are used, with which non-default options. It also generates a list of papers that should be cited to give a full description of the analysis, as shown here:

![Methods](https://github.com/husonlab/splitstree6/blob/main/doc/images/methods.png "SplitsTreeCE methods section")

 A single window might contain many tabs. The tab pane can be split to show multiple tabs side-by-side, and tabs can also be torn-off into their own separate windows.

## Methods for computing unrooted networks

The program provides a number of methods for computing unrooted networks.

### Split networks from distances

- NeighborNot (Bryant & Moulton 2004, Bryant & Huson 2023)
- Split decomposition (Bandelt and Dress, 1992)

### Split networks from trees

- Consensus network (Holland and Moulton 2003)
- Consensus outline (Huson & Cetinkaya, 2023)
- Super network (Z-closure, Huson et al 2004)
- Buneman tree 

### Split networks directly from sequences

- Parsimony splits (Bandelt and Dress 1992)

## Methods for computing haplotype networks

- Median joining (Bandelt et al, 1999)
- Minimum spanning network (Excoffier & Smouse 1994)

## Methods for computing trees

### Trees from distances

- Neighbor Joining (Saitou and Nei 1987)
- BioNJ (Gascuel 1997)
- UPGMA (Sokal and Michener 1958)

### Trees (or rooted networks) from trees (or rooted networks)

- Strict, majority and greedy consensus (Bryant 2001)

## Methods for displaying trees

- Tree drawing as rectangular or circular phylograms or cladograms
- Tree pages (p x q trees per page)
- Tanglegrams (Scornavacca et al, 2011)
-  Densi-trees (Bouckaert 2010)

## Methods for computing rooted networks from trees

- Cluster network (Huson and Rupp, 2008)
- Minimum hybridization networks (Huson and Linz 2018)

## Methods for computing distances from alignments
- Hamming distances (or observed p-distances) (Hamming 1950)
- LogDet (Steel 1994)
- Jukes Cantor (Jukes and Cantor 1969)
- K2P (Kimura 1980)
- K3ST (Kimura 1981)
- F81 (Felsenstein 1981)
- F84 (Felsenstein 1984)
- Protein ML (Swofford et al 1996)
- and others.

### Other calculations on alignments
- Estimate the number of invariable sites (Steel, Huson and Lockhart 2000)

## Other calculations on distances

- PCoA (Gower 1966)
- tSNE (Maaten and Hinton 2008)
- Delta score (Holland et al 2002) 


## Ecological and diversity indices

- Splits phylogenetic diversity
- Splits Shapley values
- Unrooted tree phylogenetic diversity
- Rooted tree (or network) phylogenetic diversity
- Rooted tree (or network) fair proportion
- Rooted tree (or network) Shapley values

## Input and output

The program supports the following input formats:

- Several flavors of Nexus
- Newick, rich Newick and extended Newick
- FastA, Clustal, MSF, Phylip, Stockholm
- Several flavors of Nexml
- SplitsTree5 files (.stree5), *not* Dendroscope3 (.dendro) files

Data can be viewed or exported in all these formats. To view or export data, context-click on the corresponding
node in the side bar and select "Show text..." or "Export..."

- When the user saves their analysis, the workflow graph is written to a file in a private flavor of the Nexus format (.stree6 extension). When the file is reopened, the program will display the saved results.

## The workflow graph

All user interactions with the program are modelled in a workflow graph. This graph has two types of nodes, data nodes
and algorithm nodes. The data nodes contain the input data, working data and other data derived during analysis. A algorithm node specifies which algorithm to run on the data represented by its main parent node to compute the data for its main child.

While most users will not interact with the workflow graph directly, one can easily add or delete nodes by context-clicking on the data nodes.

## Auto generation of methods section

Whenever the data analysis is updated, the state of affected nodes in the workflow graph is updated and the program then updates the textual description of the analysis presented in the "Methods Tab". This gives a summary of data statistics (such as number of taxa, number of trees or alignment length) and reports which algorithms have been applied (together with any non-default parameters) and provides all relevant citations.

## Fast calculation phylogenetic context for draft microbial genomes

## Commandline execution of workflows

During interactive analysis of data, the program creates a workflow graph. This graph is usually populated with data
and saved to  a file (.stree6 extension). Alternatively, the user can export the workflow using the File->Export->Worflow...
menu item. This saves the workflow without any data. This workflow can then be applied to other input data 
in a script, say, using a commandline program called workflow-run.
This program can be found in the tools directory of the  Linux and MacOS versions of SplitsTreeCE.
### workflow-run

For the sake of concreteness, assume that you have 70 files that each contain a multiple sequence alignment
of a different gene, for the same set of taxa, called alignment01.fasta ... alignment70.fasta.
Say that we want to compute  Hamming distances and then run NeighborNet on each alignment, and
then save the resulting splits to a file. 

Assume that you have interactively setup the analysis workflow using SplitsTreeCE and have saved it to a file
called script.wflow6.
To run this script on all input files contained in the current working directory, type the following:

workflow-run -w script.wflow6 -i . -f FastA -o . -e Nexus -n Splits

This will read all files in the current directory that are in FastA format, will apply the format, and will have the block named Splits in Nexus format to a file in the current working directory.

## Menu items

## Tabs
