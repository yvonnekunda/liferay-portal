AUI().add(
	'liferay-util-window',
	function(A) {
		var Util = Liferay.Util;
		var Window = Util.Window;

		Util.incrementWindowXY = function(decrement) {
			var incrementor = Window.XY_INCREMENTOR;
			var windowXY = Window.XY;

			if (decrement) {
				incrementor *= -1;
			}

			windowXY[0] += incrementor;
			windowXY[1] += incrementor;
		};

		var CONFIG_DEFAULTS_DIALOG = {
			draggable: true,
			stack: true,
			width: 720,
			xy: Window.XY,
			after: {
				visibleChange: function(event) {
					Util.incrementWindowXY(!event.newVal);
				},
				render: function(event) {
					Util.incrementWindowXY();
				}
			}
		};

		Util._openWindow = function(config) {
			var openingWindow = config.openingWindow;
			var title = config.title;
			var uri = config.uri;

			var id = config.id || A.guid();

			if (config.cache === false) {
				uri = Liferay.Util.addParams(A.guid() + '=' + (+new Date), uri);
			}

			var dialog = Window._map[id];

			if (!dialog) {
				var dialogConfig = config.dialog || {};

				var dialogIframeConfig = config.dialogIframe || {};

				A.mix(dialogConfig, CONFIG_DEFAULTS_DIALOG);

				A.mix(
					dialogIframeConfig,
					{
						id: id,
						iframeId: id,
						uri: uri
					}
				);

				if (!('zIndex' in dialogConfig)) {
					dialogConfig.zIndex = (++Liferay.zIndex.WINDOW);
				}

				dialog = new A.Dialog(dialogConfig).plug(A.Plugin.DialogIframe, dialogIframeConfig);

				Window._map[id] = dialog;

				dialog._opener = openingWindow;

				dialog.after(
					'destroy',
					function(event) {
						dialog = null;

						delete Window._map[id];
					}
				);

				dialog.iframe.after(
					'load',
					function(event) {
						var dialogIframeNode = event.currentTarget.node;

						Util.afterIframeLoaded(event);

						var dialogUtil = dialogIframeNode.get('contentWindow.Liferay.Util');

						dialogUtil.Window._opener = openingWindow;

						dialogUtil.Window._name = id;
					}
				);

				dialog.render();
			}
			else {
				dialog.show();

				dialog._syncUIPosAlign();

				dialog.iframe.set('uri', uri);
			}

			if (dialog.get('stack')) {
				A.DialogManager.bringToTop(dialog);
			}

			dialog.set('title', title);

			return dialog;
		};
	},
	'',
	{
		requires: ['aui-dialog', 'aui-dialog-iframe']
	}
);