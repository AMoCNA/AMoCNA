import {HttpClient} from "@angular/common/http";
import {environment} from "../../../environments/environment";
import {Subscription} from "rxjs";

export class BaseMetricsAdapterService {
    private readonly metricsAdapterUrl: string;


    constructor(public http: HttpClient) {
        this.metricsAdapterUrl = environment.metricsAdapterUrl;
    }

    post<T>(url: string, body: any): Subscription {
        return this.http.post<T>(this.metricsAdapterUrl + url, body)
            .subscribe({
                next: (data: any) => {
                    console.log(data);
                },
                error: (error: any) => {
                    console.log(error);
                }
            });
    }

}