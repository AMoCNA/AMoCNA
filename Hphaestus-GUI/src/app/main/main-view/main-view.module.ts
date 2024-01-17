import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';

import {MatSliderModule} from "@angular/material/slider";
import {MatSelectModule} from "@angular/material/select";
import {MatSlideToggleModule} from "@angular/material/slide-toggle";
import {MatRadioModule} from "@angular/material/radio";
import {MatInputModule} from "@angular/material/input";
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from "@angular/material/form-field";
import {MatDatepickerModule} from "@angular/material/datepicker";
import {MatChipsModule} from '@angular/material/chips'; 
import {MatCheckboxModule} from "@angular/material/checkbox";
import {MatButtonModule} from "@angular/material/button";
import {MatAutocompleteModule} from "@angular/material/autocomplete";
import {BrowserAnimationsModule} from "@angular/platform-browser/animations";
import {HephaestusService} from "../../shared/service/hephaestus/hephaestus.service";
import {MainViewComponent} from "./main-view.component";
import {HttpClientModule} from "@angular/common/http";
import {DragDropModule} from "@angular/cdk/drag-drop";
import {HephaestusTableComponent} from "../../shared/components/table/hephaestus-table/hephaestus-table.component";
import { SearchFilterComponent } from 'src/app/shared/components/search-filter/search-filter/search-filter.component';
import { ReactiveFormsModule } from '@angular/forms';
import {MatPaginatorModule} from '@angular/material/paginator';
import {MatTooltipModule} from "@angular/material/tooltip";

@NgModule({
  declarations: [
    MainViewComponent,
    HephaestusTableComponent,
    SearchFilterComponent,
  ],
    imports: [
        BrowserAnimationsModule,
        MatAutocompleteModule,
        MatButtonModule,
        MatCheckboxModule,
        MatChipsModule,
        MatDatepickerModule,
        MatFormFieldModule,
        MatIconModule,
        MatInputModule,
        MatRadioModule,
        MatSelectModule,
        MatSliderModule,
        MatSlideToggleModule,
        DragDropModule,
        BrowserModule,
        HttpClientModule,
        ReactiveFormsModule,
        MatPaginatorModule,
        MatTooltipModule,
    ],
  providers: [HephaestusService],
  exports: [
    MainViewComponent
  ],
  bootstrap: []
})
export class MainViewModule { }
