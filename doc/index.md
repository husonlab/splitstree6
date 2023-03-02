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

## Methods for computing trees

## Methods for computing unrooted networks

## Methods for computing rooted networks

## Input and output

## The workflow graph

## Auto generation of methods section

## Commandline execution of workflows
