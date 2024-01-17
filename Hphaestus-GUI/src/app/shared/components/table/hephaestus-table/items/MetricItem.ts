export class MetricItem {

    public labels: Map<string, string> = new Map<string, string>();

    public readonly isQuery: boolean = false;

    private parents: Set<MetricItem> = new Set<MetricItem>();

    private children: Set<MetricItem> = new Set<MetricItem>();

    constructor(labels: Map<string, string>, isQuery: boolean) {
        this.labels = labels;
        this.isQuery = isQuery;
    }

    public getTextRepresentation(): string[] {
        return Array.from((this.labels.entries())).map(pair => pair[0] + ': ' + pair[1]);
    }

    public checkConflict(other: MetricItem): boolean {
        const shorter = ((other.labels.size < this.labels.size) ? other : this);
        const longer = ((other.labels.size >= this.labels.size) ? other : this);
        for (const key of shorter.labels.keys()) {
            if (longer.labels.has(key) && shorter.labels.get(key) === longer.labels.get(key)) {
                continue;
            } else {
                return false;
            }
        }
        shorter.children.add(longer);
        longer.parents.add(shorter);
        return true;
        
    }

    public delete() {
        for (const parent of this.parents) {
            parent.children.delete(this);
        }
        for (const child of this.children) {
            child.parents.delete(this);
        }
        this.parents = new Set<MetricItem>();
        this.children = new Set<MetricItem>();
    }

    public hasConflict(): boolean {
        return this.parents.size !== 0;
    }
}