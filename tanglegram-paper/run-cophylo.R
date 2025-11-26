#!/usr/bin/env Rscript

# args: input filename
args <- commandArgs(trailingOnly=TRUE)
if (length(args) < 1) {
  stop("Usage: run_cophylo.R <file-with-2-newicks>")
}
fname <- args[1]

suppressMessages({
  library(ape)
  library(phytools)
})


# Read two Newick strings
lines <- readLines(fname)
if (length(lines) < 2) stop("File must contain at least two lines with Newick strings")

t1 <- read.tree(text=lines[1])
t2 <- read.tree(text=lines[2])

# Align to common taxa
X <- intersect(t1$tip.label, t2$tip.label)
t1 <- keep.tip(t1, X)
t2 <- keep.tip(t2, X)
n <- length(X)

# Start timer
t_start <- proc.time()

# Run cophylo (two-sided optimization), without plotting
cp <- cophylo(t1, t2, rotate=TRUE, plot=FALSE)

# Stop timer and compute elapsed wall-clock time
t_end <- proc.time()
elapsed <- as.integer((t_end - t_start)[["elapsed"]])

# Extract optimized leaf order
order1 <- cp$trees[[1]]$tip.label
order2 <- cp$trees[[2]]$tip.label

pos1 <- setNames(seq_along(order1), order1)
pos2 <- setNames(seq_along(order2), order2)

# Taxon displacement (Spearman footrule distance)
TD <- sum(abs(pos1[X] - pos2[X]))

# Crossing count (O(n^2))
perm <- as.integer(pos2[order1])
CX <- 0L
for (i in 1:(n-1)) {
  for (j in (i+1):n) {
    if (perm[i] > perm[j]) CX <- CX + 1L
  }
}



cat(sprintf("IN=%s\tMD=cophylo\tn=%d\tTD=%d\tCX=%d\tTM=%d\n", fname, n, TD, CX, elapsed))
