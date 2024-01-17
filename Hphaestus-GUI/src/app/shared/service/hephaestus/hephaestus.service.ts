import { Injectable } from '@angular/core';
import {BaseService} from "../base-service";
import {HttpClient} from "@angular/common/http";
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class HephaestusService extends BaseService {

  private saveSimpleMetricUrl: string = '/hephaestus/queries/simple';
  private saveCustomMetricUrl: string = '/hephaestus/queries/custom'

  constructor(http: HttpClient) {
    super(http);
  }

  public saveSimpleMetrics(metrics: {}) {
    return this.put(this.saveSimpleMetricUrl, metrics);
  }

  public getSavedSimpleMetrics(): Observable<any[]>{
    return this.get<any[]>(this.saveSimpleMetricUrl);
  }

  public saveCustomMetrics(metrics: {}) {
    return this.put(this.saveCustomMetricUrl, metrics);
  }

  public getSavedCustomMetrics(): Observable<any[]>{
    return this.get<any[]>(this.saveCustomMetricUrl);
  }

}
