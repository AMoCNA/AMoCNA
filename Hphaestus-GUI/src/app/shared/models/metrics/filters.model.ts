
export class Filters {

    constructor (public filters: Map<string, string> = new Map()) { }

    add(label: string, value: string) {
        this.filters.set(label, value);
    }

    delete(label: string) {
        this.filters.delete(label);
    }

    toJSON(): any {
        const mapObject: any = {};
        for (let entry of this.filters.entries()){
            mapObject[entry[0]] = entry[1];
        }
        console.log( { 'filters': mapObject })
        return { 'filters': mapObject };
    }

}