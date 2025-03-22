#!/usr/bin/env python3
import subprocess
import json
import matplotlib.pyplot as plt
import numpy as np
import os
from datetime import datetime
import re

# Java versions to test
JAVA_VERSIONS = {
    "Java 8": "8.0.402-amzn",
    "Java 17": "17.0.9-amzn",
    "Java 21": "21.0.2-open",
    "Java 24": "24-amzn"
}

def run_benchmark(java_version):
    """Run benchmark with specified Java version"""
    print(f"\nRunning benchmarks with {java_version}...")
    
    # Set JAVA_HOME for the specific version
    env = os.environ.copy()
    env["JAVA_HOME"] = f"/Users/hazar.nazari/.sdkman/candidates/java/{java_version}"
    env["PATH"] = f"{env['JAVA_HOME']}/bin:{env['PATH']}"
    
    # Clean and package the project
    subprocess.run(["mvn", "clean", "package"], env=env, check=True)
    
    # Run the benchmark and capture output
    result = subprocess.run(["java", "-jar", "target/benchmarks.jar"], env=env, capture_output=True, text=True)
    return parse_benchmark_output(result.stdout)

def parse_benchmark_output(output):
    """Parse benchmark results from JMH output"""
    results = {}
    pattern = r"JavaVersionBenchmark\.(\w+)\s+avgt\s+\d+\s+([\d,.]+)\s+±"
    
    for line in output.split('\n'):
        match = re.search(pattern, line)
        if match:
            benchmark_name = match.group(1)
            score = float(match.group(2).replace(',', ''))
            results[benchmark_name] = score
    
    return results

def create_visualization(all_results):
    """Create a bar chart comparing the results"""
    if not all_results:
        print("No results to visualize")
        return

    benchmarks = list(next(iter(all_results.values())).keys())
    java_versions = list(all_results.keys())
    
    # Set up the plot
    plt.figure(figsize=(15, 10))
    x = np.arange(len(benchmarks))
    width = 0.15
    multiplier = 0
    
    # Plot bars for each Java version
    for version, results in all_results.items():
        offset = width * multiplier
        values = [results.get(benchmark, 0) for benchmark in benchmarks]
        rects = plt.bar(x + offset, values, width, label=version)
        multiplier += 1
    
    # Customize the plot
    plt.xlabel('Benchmark Type')
    plt.ylabel('Average Time (microseconds)')
    plt.title('Java Version Performance Comparison')
    plt.xticks(x + width * (len(java_versions) - 1) / 2, benchmarks, rotation=45, ha='right')
    plt.legend(loc='upper left', bbox_to_anchor=(1, 1))
    
    # Adjust layout and save
    plt.tight_layout()
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    plt.savefig(f'benchmark_results_{timestamp}.png', dpi=300, bbox_inches='tight')
    print(f"\nResults visualization saved as benchmark_results_{timestamp}.png")

def main():
    all_results = {}
    
    for version_name, version in JAVA_VERSIONS.items():
        try:
            results = run_benchmark(version)
            if results:  # Only add if we got valid results
                all_results[version_name] = results
        except Exception as e:
            print(f"Error running benchmark for {version_name}: {e}")
    
    if all_results:
        create_visualization(all_results)
        
        # Print numerical results
        print("\nNumerical Results (microseconds):")
        print("\nBenchmark", end="")
        for version in all_results.keys():
            print(f"\t{version:>10}", end="")
        print()
        print("-" * 80)
        
        benchmarks = list(next(iter(all_results.values())).keys())
        for benchmark in benchmarks:
            print(f"{benchmark:<20}", end="")
            for version in all_results.keys():
                print(f"\t{all_results[version].get(benchmark, 0):>10.2f}", end="")
            print()
    else:
        print("No benchmark results were collected.")

if __name__ == "__main__":
    main() 