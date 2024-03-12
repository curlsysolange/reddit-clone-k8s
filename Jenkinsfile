pipeline {
    agent any
    
    tools {
        nodejs 'Nodejs'
    }
    
    environment {
        SCANNER_HOME = tool 'sonar-scanner'
        DOCKERHUB_CREDENTIALS = credentials('Docker_hub')
    }
    
    stages {
        stage('Clean workspace') {
            steps {
                cleanWs()
            }
        }
        
        stage('Checkout') {
            steps {
                git branch: 'main', url: 'https://github.com/curlsysolange/reddit-clone-k8s.git'
            }
        }

        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('sonar-server') {
                    sh "$SCANNER_HOME/bin/sonar-scanner -Dsonar.projectName=reddit-clone-k8s -Dsonar.projectKey=reddit-clone-k8s"
                }
            }
        }

        stage('Quality Gate') {
            steps {
                script {
                    waitForQualityGate abortPipeline: false, credentialsId: 'Sonar-Token'
                }
            }
        }

        stage('NPM') {
            steps {
                sh 'npm install'
            }
        }

        stage('Trivy File Scan') {
            steps {
                script {
                    sh '/usr/local/bin/trivy fs . > trivy_result.txt'
                }
            }
        }

        stage('Login to DockerHUB') {
            steps {
                script {
                    sh "echo $DOCKERHUB_CREDENTIALS_PSW | docker login -u $DOCKERHUB_CREDENTIALS_USR --password-stdin"
                    echo 'Login Succeeded'
                }
            }
        }

        stage('Security Testing') {
            steps {
                dependencyCheck additionalArguments: '--scan ./ --disableYarnAudit --disableNodeAudit --nvdApiKey 4bdf4acc-8eae-45c1-bfc4-844d549be812', odcInstallation: 'DP'
                dependencyCheckPublisher pattern: '**/dependency-check-report.xml'
            }
        }

        stage('Docker Build') {
            steps {
                script {
                    sh 'docker build -t curlsysolange/reddit-clone-k8s:latest .' 
                    echo "Image Build Successfully"
                }
            }
        }

        stage('OWASP FILE SCAN') {
            steps {
                dependencyCheck additionalArguments: '--scan ./ --disableYarnAudit --disableNodeAudit --nvdApiKey 4bdf4acc-8eae-45c1-bfc4-844d549be812', odcInstallation: 'DP'
                dependencyCheckPublisher pattern: '**/dependency-check-report.xml'
            }
        }

        stage('Docker Push') {
            steps {
                script {
                    sh 'docker push curlsysolange/reddit-clone-k8s:latest'
                    echo "Push Image to Registry"
                }
            }
        }

        stage('Trivy Image Scan') {
            steps {
                script {
                    sh '/usr/local/bin/trivy image curlsysolange/reddit-clone-k8s:latest > trivy_image_result.txt'
                    sh 'pwd'
                }
            }
        }

        stage('Containerization Deployment') {
            steps {
                script {
                    def containername = 'reddit-clone-k8s'
                    def isRunning = sh(script: "docker ps -a | grep ${containername}", returnStatus: true)
                    if (isRunning == 0) {
                        sh "docker stop ${containername}"
                        sh "docker rm ${containername}"
                    }
                    sh "docker run -d -p 3000:3000 --name ${containername} curlsysolange/reddit-clone-k8s:latest"
                }
            }
        }

        stage('Deploy') {
            steps {
                sh 'kubectl apply -f deployment.yaml'
                sh 'kubectl apply -f service.yaml'
                // If using Ingress
                sh 'kubectl apply -f ingress.yaml'
            }
        }

        stage('Scan Kubernetes Cluster') {
            steps {
                script {                    
                    // Get list of pods in the cluster
                    def pods = sh(script: 'kubectl get pods --all-namespaces -o=jsonpath="{range .items[*]}{.metadata.namespace}/{.metadata.name}{"\\n"}{end}"', returnStdout: true).trim().split('\n')
                    
                    // Iterate over each pod
                    for (pod in pods) {
                        def namespace = pod.split('/')[0]
                        def podName = pod.split('/')[1]
                        
                        // Get list of containers in the pod
                        def containers = sh(script: "kubectl get pod -n $namespace $podName -o=jsonpath='{range .spec.containers[*]}{.name}{\"\\n\"}{end}'", returnStdout: true).trim().split('\n')
                        
                        // Iterate over each container in the pod
                        for (container in containers) {
                            // Get container image name
                            def image = sh(script: "kubectl get pod -n $namespace $podName -o=jsonpath=\"{.spec.containers[?(@.name=='$container')].image}\"", returnStdout: true).trim()
                            
                            // Run Trivy to scan the image
                            echo "Scanning image $image in pod $podName in namespace $namespace"
                            sh "trivy image -n --severity HIGH,MEDIUM $image"
                        }
                    }
                }
            }
        }
    }
}
