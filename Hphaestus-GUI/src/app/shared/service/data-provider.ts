import {Injectable} from "@angular/core";

@Injectable()
export class DataProvider {

    private filters: Map<string, string> = new Map();

    constructor() {}

    getFilters(): Map<string, string> {
        return this.filters;
    }

    setFilters(filters: Map<string, string>): void {
        this.filters = filters;
    }
}