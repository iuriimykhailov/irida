/**
 * Author: Josh Adam <josh.adam@phac-aspc.gc.ca>
 * Date:   2013-04-30
 * Time:   8:19 AM
 */

angular.module('irida')
  .directive('notifier', function () {
    return {
      restrict: 'E',
      scope: {
        message: '@attrMessege',
        icon: '@attrIcon'
      },
      replace: true,
      controller: function ($scope, $attrs, $element, $timeout) {
        var time, timer;
        $scope.hidden = true;
        $scope.$on('notify', function () {
          'use strict';
          if (time < 5) {
            $timeout.cancel(timer);
          }
          time = 5;
          $scope.hidden = false;
          countDown();
        });

        function countDown() {
          'use strict';
          time -= 1;
          if (time > 0) {
            timer = $timeout(countDown, 1000);
          }
          else {
            $timeout.cancel(timer);
            $scope.hidden = true;
          }
        }
      },
      template: '<div style="display: none" class="ng-cloak notifier" data-ng-hide="hidden" data-ng-animate="\'notifier\'"><i class="icon-{{icon}}"></i> <span>{{message}}</span></div>',
      link: function () {
        'use strict';
      }
    };
  });