			var container;
			var camera, scene, renderer;
			var projector, plane;
			var mouse2D, mouse3D, raycaster, theta = 45,
			isShiftDown = false, isCtrlDown = false,
			target = new THREE.Vector3( 0, 200, 0 );
			var ROLLOVERED;

			init();
			animate();

			function init() {

				var container = document.getElementById('drawarea');
				//drawElement.parentNode.replaceChild(container, drawElement);
				
				//document.body.appendChild( container );

				var info = document.createElement( 'div' );
				info.style.position = 'absolute';
				info.style.top = '0px';
				info.style.width = '100%';
				info.style.textAlign = 'center';
				info.innerHTML = '<a href="http://threejs.org" target="_blank">three.js</a> - voxel painter<br><strong>click</strong>: add voxel, <strong>control + click</strong>: remove voxel, <strong>shift</strong>: rotate, <a href="javascript:save();return false;">save .png</a>';
				container.appendChild( info );

				camera = new THREE.PerspectiveCamera( 40, window.innerWidth / window.innerHeight, 1, 10000 );
				camera.position.y = 800;

				scene = new THREE.Scene();

				// Grid

				var size = 500, step = 50;

				var geometry = new THREE.Geometry();

				for ( var i = - size; i <= size; i += step ) {

					geometry.vertices.push( new THREE.Vector3( - size, 0, i ) );
					geometry.vertices.push( new THREE.Vector3(   size, 0, i ) );

					geometry.vertices.push( new THREE.Vector3( i, 0, - size ) );
					geometry.vertices.push( new THREE.Vector3( i, 0,   size ) );

				}

				var material = new THREE.LineBasicMaterial( { color: 0x000000, opacity: 0.2 } );

				var line = new THREE.Line( geometry, material );
				line.type = THREE.LinePieces;
				scene.add( line );

				//

				projector = new THREE.Projector();

				plane = new THREE.Mesh( new THREE.PlaneGeometry( 1000, 1000 ), new THREE.MeshBasicMaterial() );
				plane.rotation.x = - Math.PI / 2;
				plane.visible = false;
				scene.add( plane );

				mouse2D = new THREE.Vector3( 0, 10000, 0.5 );

				// Lights

				var ambientLight = new THREE.AmbientLight( 0x606060 );
				scene.add( ambientLight );

				var directionalLight = new THREE.DirectionalLight( 0xffffff );
				directionalLight.position.x = Math.random() - 0.5;
				directionalLight.position.y = Math.random() - 0.5;
				directionalLight.position.z = Math.random() - 0.5;
				directionalLight.position.normalize();
				scene.add( directionalLight );

				var directionalLight = new THREE.DirectionalLight( 0x808080 );
				directionalLight.position.x = Math.random() - 0.5;
				directionalLight.position.y = Math.random() - 0.5;
				directionalLight.position.z = Math.random() - 0.5;
				directionalLight.position.normalize();
				scene.add( directionalLight );

				renderer = new THREE.CanvasRenderer();
				renderer.setSize( window.innerWidth, window.innerHeight );

				container.appendChild(renderer.domElement);

				document.addEventListener( 'mousemove', onDocumentMouseMove, false );
				document.addEventListener( 'mousedown', onDocumentMouseDown, false );
				document.addEventListener( 'keydown', onDocumentKeyDown, false );
				document.addEventListener( 'keyup', onDocumentKeyUp, false );

				//

				window.addEventListener( 'resize', onWindowResize, false );

			}

			function onWindowResize() {

				camera.aspect = window.innerWidth / window.innerHeight;
				camera.updateProjectionMatrix();

				renderer.setSize( window.innerWidth, window.innerHeight );

			}

			function onDocumentMouseMove( event ) {

				event.preventDefault();

				mouse2D.x = ( event.clientX / window.innerWidth ) * 2 - 1;
				mouse2D.y = - ( event.clientY / window.innerHeight ) * 2 + 1;

				var intersects = raycaster.intersectObjects( scene.children );

				if ( intersects.length > 0 ) {

					if ( ROLLOVERED ) ROLLOVERED.color.setHex( 0x00ff80 );

					ROLLOVERED = intersects[ 0 ].face;
					ROLLOVERED.color.setHex( 0xff8000 )

				}

			}

			function onDocumentMouseDown( event ) {

				event.preventDefault();

				var intersects = raycaster.intersectObjects( scene.children );

				if ( intersects.length > 0 ) {

					var interect = intersects[ 0 ];

					if ( isCtrlDown ) {

						if ( interect.object != plane ) {

							scene.remove( interect.object );

						}

					} else {

						var normal = interect.face.normal.clone();
						normal.applyMatrix4( interect.object.matrixRotationWorld );

						var position = new THREE.Vector3().addVectors( interect.point, normal );

						var geometry = new THREE.CubeGeometry( 50, 50, 50 );

						for ( var i = 0; i < geometry.faces.length; i ++ ) {

							geometry.faces[ i ].color.setHex( 0x00ff80 );

						}

						var material = new THREE.MeshLambertMaterial( { vertexColors: THREE.FaceColors } );

						var voxel = new THREE.Mesh( geometry, material );
						voxel.position.x = Math.floor( position.x / 50 ) * 50 + 25;
						voxel.position.y = Math.floor( position.y / 50 ) * 50 + 25;
						voxel.position.z = Math.floor( position.z / 50 ) * 50 + 25;
						voxel.matrixAutoUpdate = false;
						voxel.updateMatrix();
						scene.add( voxel );

					}

				}
			}

			function onDocumentKeyDown( event ) {

				switch( event.keyCode ) {

					case 16: isShiftDown = true; break;
					case 17: isCtrlDown = true; break;

				}

			}

			function onDocumentKeyUp( event ) {

				switch( event.keyCode ) {

					case 16: isShiftDown = false; break;
					case 17: isCtrlDown = false; break;

				}
			}

			function save() {

				window.open( renderer.domElement.toDataURL('image/png'), 'mywindow' );

			}

			//

			function animate() {
				requestAnimationFrame( animate );
				render();
			}

			function render() {

				if ( isShiftDown ) {

					theta += mouse2D.x * 3;

				}

				camera.position.x = 1400 * Math.sin( theta * Math.PI / 360 );
				camera.position.z = 1400 * Math.cos( theta * Math.PI / 360 );
				camera.lookAt( target );

				raycaster = projector.pickingRay( mouse2D.clone(), camera );

				renderer.render( scene, camera );

			}
