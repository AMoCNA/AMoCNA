"""Statistical utilities for SCIG evaluation metrics."""

import math
from dataclasses import dataclass

@dataclass
class DescriptiveStats:
    """Descriptive statistics for a measurement series."""
    mean: float
    std_dev: float
    min_val: float
    max_val: float
    median: float
    n: int

    def latex_str(self, precision: int = 1) -> str:
        """Format as $mean \\pm std_dev$ for LaTeX."""
        return f"${self.mean:.{precision}f} \\pm {self.std_dev:.{precision}f}$"

def compute_stats(values: list[float]) -> DescriptiveStats:
    """Compute descriptive statistics from a list of measurements."""
    n = len(values)
    if n == 0:
        return DescriptiveStats(0.0, 0.0, 0.0, 0.0, 0.0, 0)
    mean = sum(values) / n
    variance = sum((x - mean) ** 2 for x in values) / (n - 1) if n > 1 else 0.0
    std_dev = math.sqrt(variance)
    sorted_vals = sorted(values)
    median = sorted_vals[n // 2] if n % 2 else (sorted_vals[n // 2 - 1] + sorted_vals[n // 2]) / 2.0
    return DescriptiveStats(mean=mean, std_dev=std_dev, min_val=min(values),
                           max_val=max(values), median=median, n=n)

def jaccard_similarity(set_a: set, set_b: set) -> float:
    """Compute Jaccard similarity index between two sets."""
    if not set_a and not set_b:
        return 1.0
    intersection = set_a & set_b
    union = set_a | set_b
    return len(intersection) / len(union) if union else 0.0
