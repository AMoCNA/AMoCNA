export class CustomQuery {
    tag: string;
    query: string;

    constructor(tag: string, query: string) {
        this.tag = tag;
        this.query = query;
    }

    toJSON(): { tag: string, queryString: string } {
        return {
            tag: this.tag,
            queryString: this.query
        };
    }
}
