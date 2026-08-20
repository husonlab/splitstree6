#!/opt/homebrew/bin/python3.12
import csv
import sys

def main():
    if len(sys.argv) != 3:
        print(f"Usage: {sys.argv[0]} input.csv output.csv")
        sys.exit(1)

    input_file, output_file = sys.argv[1], sys.argv[2]

    # Read n and then an n×n matrix
    with open(input_file, newline="") as f:
        reader = csv.reader(f)
        try:
            first_line = next(reader)
        except StopIteration:
            print("Error: input file is empty")
            sys.exit(1)

        if len(first_line) != 1:
            print("Error: first line must contain exactly one integer n (matrix is n×n)")
            sys.exit(1)

        try:
            n = int(first_line[0])
        except ValueError:
            print("Error: first line must be an integer n")
            sys.exit(1)

        matrix = []
        for row in reader:
            if not row:  # skip blank lines
                continue
            if len(row) != n:
                print(f"Error: expected {n} values per row, got {len(row)}")
                sys.exit(1)
            try:
                matrix.append([float(x) for x in row])
            except ValueError:
                print("Error: matrix entries must be numeric")
                sys.exit(1)

        if len(matrix) != n:
            print(f"Error: expected {n} rows, got {len(matrix)}")
            sys.exit(1)

    # Write all unordered pairs (i<j) with their distances
    with open(output_file, "w", newline="") as f:
        writer = csv.writer(f)
        for i in range(n):
            for j in range(i + 1, n):
                writer.writerow([i, j, matrix[i][j]])

if __name__ == "__main__":
    main()