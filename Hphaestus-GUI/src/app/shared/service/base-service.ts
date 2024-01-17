import {HttpClient} from "@angular/common/http";
import {environment} from "../../../environments/environment";
import {Observable} from "rxjs";

export class BaseService {
    private readonly backendUrl: string;

    constructor(public http: HttpClient) {
        this.backendUrl = environment.backendUrl;
    }

    get<T>(url: string): Observable<T> {
        return this.http.get<T>(this.backendUrl + url);
    }

    post<T>(url: string, body: any): Observable<T> {
        return this.http.post<T>(this.backendUrl + url, body);
    }

    put<T>(url: string, body: any) {
        this.http.put<any>(this.backendUrl + url, body)
            .subscribe({
                next: (data: any) => {
                    console.log(data);
                },
                error: (error: any) => {
                    console.log(error);
                }
            })
    }

}