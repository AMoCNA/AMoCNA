import { Injectable } from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {BaseMetricsAdapterService} from "../base-metrics-adapter.service";

@Injectable({
    providedIn: 'root'
})
export class MetricsAdapterService extends BaseMetricsAdapterService {

    private runRulesUrl: string = '/metrics-adapter/metrics';

    constructor(http: HttpClient) {
        super(http);
    }

    public runRules(metrics: {}) {
        return this.post(this.runRulesUrl, metrics);
    }

}
