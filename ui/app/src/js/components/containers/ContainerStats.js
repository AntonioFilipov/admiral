/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import ContainerStatsVue from 'components/containers/ContainerStatsVue.html';
import { RadialProgress } from 'admiral-ui-common';
import { NetworkTrafficVisualization } from 'admiral-ui-common';
import { formatUtils } from 'admiral-ui-common';
import utils from 'core/utils';

const NA = i18n.t('unavailable');

var ContainerStats = Vue.extend({
  template: ContainerStatsVue,

  props: {
    model: { required: true },
    containerStopped: {
      required: false,
      type: Boolean,
      default: false
    }
  },

  computed: {
    onVchHost: function() {
      return utils.isContainerOnVchHost(this.model.instance);
    }
  },

  ready: function() {
    this.cpuStats = new RadialProgress($(this.$el).find('.cpu-stats')[0]).diameter(150).value(0)
      .majorTitle(NA).label(i18n.t('app.container.details.cpu')).render();

    this.memoryStats = new RadialProgress($(this.$el).find('.memory-stats')[0]).diameter(150)
      .value(0).majorTitle(NA).label(i18n.t('app.container.details.memory')).render();

    if (!this.onVchHost) {
      this.networkStats = new NetworkTrafficVisualization($(this.$el)
          .find('.network-stats')[0], i18n);
    }

    resetStats.call(this);

    this.modelUnwatch = this.$watch('model.instance.powerState', this.onContainerUpdate);
  },

  attached: function() {
    this.modelUnwatch = this.$watch('model.stats', this.onDataUpdate);

    if (this.model.instance.powerState === 'STOPPED') {
      this.containerStopped = true;
    }
  },

  detached: function() {
    this.modelUnwatch();
  },

  filters: {
    calculateStatsClass: function(percentage) {
      if (!percentage) {
        return '';
      }

      if (percentage < 50) {
        return 'info';
      }

      if (percentage < 80) {
        return 'warning';
      }

      return 'danger';
    }
  },

  methods: {
    onDataUpdate: function(newData) {
      if (newData && !this.containerStopped) {
        // CPU
        var cpuPercentage = newData.cpuUsage;
        if (typeof cpuPercentage !== 'undefined') {
          this.cpuStats.value(cpuPercentage).majorTitle(null).render();
        } else {
          this.cpuStats.value(0).majorTitle(NA).render();
        }
        this.cpuPercentage = cpuPercentage;

        // Memory
        var memoryPercentage;
        if (!newData.memUsage || !newData.memLimit) {
          memoryPercentage = 0;
        } else {
          memoryPercentage = (newData.memUsage / newData.memLimit) * 100;
        }
        this.memoryPercentage = memoryPercentage;

        var memoryUsage = formatUtils.formatBytes(newData.memUsage);
        var memoryLimit = formatUtils.formatBytes(newData.memLimit);

        this.memoryStats.majorTitle(memoryUsage).minorTitle(memoryLimit).value(memoryPercentage)
          .render();

        // Network
        if (this.networkStats) {
          this.networkStats.setData(newData.networkIn, newData.networkOut);
        }
      } else {
        resetStats.call(this);
      }
    },
    onContainerUpdate: function(data) {
      if (data === 'STOPPED') {
        this.containerStopped = true;
        resetStats.call(this);
      } else {
        this.containerStopped = false;
      }
    }
  }
});

function resetStats() {
  this.cpuStats.value(0).majorTitle(NA).render();
  this.memoryStats.majorTitle(NA).minorTitle(NA).value(0).render();

  if (this.networkStats) {
    this.networkStats.reset(NA);
  }
}

Vue.component('container-stats', ContainerStats);

export default ContainerStats;
