import { Injectable } from '@angular/core';
import {BaseService} from "../base-service";
import {HttpClient} from "@angular/common/http";
import { Observable, map, Subject, of } from 'rxjs';
import { ListResponse } from '../../models/reponse/ListResponse.model';
import { Metric } from '../../models/metrics/Metric';
import { QueryResponse } from '../../models/reponse/QueryResponse.model';
import { Filters } from '../../models/metrics/filters.model';

@Injectable({
  providedIn: 'root'
})
export class PrometheusService extends BaseService {

  private readonly displayedQueryResult: Subject<Array<Metric>> = new Subject();

  constructor(http: HttpClient) {
    super(http);
  }

  public getDisplayableMetrics(): Observable<Array<Metric>> {
    return this.displayedQueryResult.asObservable();
  }

  public getLabels(): Observable<ListResponse> {
    return this.get<ListResponse>("/prometheus/labels");
  }

  public getLabelValues(label: string): Observable<ListResponse> {
    return this.get<ListResponse>("/prometheus/values?label=" + encodeURIComponent(label));
  }

  private queryResponseToMetrics(response: QueryResponse): Array<Metric> {
    if (!response || !response.data) {
      // no data returned
      return [];
    }

    const result = []
    for (let metric of response.data.result) {
      const name: string = metric.metric["__name__"];
      const labels: Map<string, string> = new Map();
      for (let label in metric.metric) {
        labels.set(label, metric.metric[label]);
      }
      const resultMetric: Metric = { name, labels };
      result.push(resultMetric);
    }
    return result;
  }

  public query(query: string | null): Observable<Array<Metric>> {
    if (!query) return of([]);
    return this.post<QueryResponse>("/prometheus/query", query).pipe(
      map(this.queryResponseToMetrics)
    );
  }

  public queryByFilters(filters: Filters) {
    return this.post<QueryResponse>("/prometheus/query/filters", filters.toJSON()).pipe(
      map(this.queryResponseToMetrics)
    );
  }

  public displayResult(queryResult: Observable<Array<Metric>>){
    queryResult.subscribe((metrics) => {
      this.displayedQueryResult.next(metrics);
    });
  }

}
