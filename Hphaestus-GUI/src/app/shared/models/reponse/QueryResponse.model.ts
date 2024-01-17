
export interface QueryResponse {

  status: string;
  data: QueryResult;

}

interface QueryResult {

  resultType: string;
  result: Array<QueryResultMetric>;

}

interface QueryResultMetric {

  metric: Labels;
  value: Array<any>;

}

interface Labels {

  [label: string]: string;

}