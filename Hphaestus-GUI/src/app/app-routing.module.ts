import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import {MainViewComponent} from "./main/main-view/main-view.component";
import {AdvancedViewComponent} from "./advanced/advanced-view/advanced-view.component";

const routes: Routes = [
  { path: '', component: MainViewComponent },
  { path: 'advanced', component: AdvancedViewComponent }
];

@NgModule({
  imports: [RouterModule.forRoot(routes, {useHash:true})],
  exports: [RouterModule]
})
export class AppRoutingModule { }
