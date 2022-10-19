clear all
ST4Convergence

figure(1)
subplot(4,2,1);
plot(ST4_00(:,1),log(ST4_00(:,3)),'x');
subplot(4,2,3);
plot(ST4_10(:,1),log(ST4_10(:,3)),'o');
subplot(4,2,5);
plot(ST4_20(:,1),log(ST4_20(:,3)),'rx');
subplot(4,2,7);
plot(ST4_60(:,1),log(ST4_60(:,3)),'ro');


subplot(4,2,2);
plot(ST4_00(:,1),ST4_00(:,5),'x');
subplot(4,2,4);
plot(ST4_10(:,1),ST4_10(:,5),'o');
subplot(4,2,6);
plot(ST4_20(:,1),ST4_20(:,5),'rx');
subplot(4,2,8);
plot(ST4_60(:,1),ST4_60(:,5),'ro');

figure(2)
subplot(4,2,1);
plot(ST4_00i(:,1),log(ST4_00(:,3)),'x');
subplot(4,2,3);
plot(ST4_10i(:,1),log(ST4_10(:,3)),'o');
subplot(4,2,5);
plot(ST4_20i(:,1),log(ST4_20(:,3)),'rx');
subplot(4,2,7);
plot(ST4_60i(:,1),log(ST4_60(:,3)),'ro');


subplot(4,2,2);
plot(ST4_00i(:,1),ST4_00(:,5),'x');
subplot(4,2,4);
plot(ST4_10i(:,1),ST4_10(:,5),'o');
subplot(4,2,6);
plot(ST4_20i(:,1),ST4_20(:,5),'rx');
subplot(4,2,8);
plot(ST4_60i(:,1),ST4_60(:,5),'ro');