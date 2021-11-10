<template>
  <div class="leaflet">
    <div class="geo-map">
      <l-map
        v-model:zoom="zoom"
         :zoomAnimation="true"
        :center="openNMSHeadQuarter"
      >
      <l-tile-layer
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        attribution='&copy; <a href="http://osm.org/copyright">OpenStreetMap</a> contributors'
      ></l-tile-layer>
      <l-control-layers />
      </l-map>
    </div>
  </div>
</template>
<script setup lang ="ts">
import { computed, watch, ref, nextTick } from "vue";
import "leaflet/dist/leaflet.css";
import {
  LMap,
  LTileLayer,
  LMarker,
  LControlLayers,
  // LTooltip,
  LPopup,
  LPolyline,
} from "@vue-leaflet/vue-leaflet";
import { Vue } from "vue-class-component";


</script>

<style lang="scss" scoped>
.geo-map {
  height: 80vh;
}
</style>
